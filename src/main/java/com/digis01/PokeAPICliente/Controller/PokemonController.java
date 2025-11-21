package com.digis01.PokeAPICliente.Controller;

import com.digis01.PokeAPICliente.DAO.IRepositoryFavorito;
import com.digis01.PokeAPICliente.DAO.IRepositoryUsuario;
import com.digis01.PokeAPICliente.DTO.PageDTO;
import com.digis01.PokeAPICliente.DTO.PokemonCardDTO;
import com.digis01.PokeAPICliente.DTO.PokemonFullDTO;
import com.digis01.PokeAPICliente.JPA.Favoritos;
import com.digis01.PokeAPICliente.JPA.Usuario;
import com.digis01.PokeAPICliente.ML.Result;
import com.digis01.PokeAPICliente.Service.PokedexCacheService;
import com.digis01.PokeAPICliente.Service.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// ⬇️ nuevo import para obtener la URL actual (redirectTo)
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;

@RequestMapping("pokemon")
@Controller
public class PokemonController {

    @Autowired
    private IRepositoryUsuario iRepositoryUsuario;
    
    @Autowired
    private IRepositoryFavorito iRepositoryFavorito;

    @Autowired
    private UsuarioService usuarioService;

    private final PokedexCacheService svc;

    public PokemonController(PokedexCacheService svc) {
        this.svc = svc;
    }

    /* ================== INDEX ================== */
    @GetMapping
    public String index(
            Model model,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "types", required = false) String typesCsv,
            @RequestParam(value = "loc", required = false) String loc, 
            @RequestParam(value = "hab", required = false) String hab, 
            @RequestParam(value = "id", required = false) String id 
    ) {
        
        svc.warmupAsync(false);

        Map<String, Object> st = svc.status();
        boolean warming = Boolean.TRUE.equals(st.get("warming"));
        int cached = ((Number) st.getOrDefault("count", 0)).intValue();
        boolean emptySnapshot = svc.allCards().isEmpty();

        if (warming || cached == 0 || emptySnapshot) {
            pushLoading(model);
            model.addAttribute("redirectTo", "/pokemon");
            return "PokemonLoading";
        }

        // Trae todo de memoria
        List<PokemonCardDTO> all = svc.allCards();

        // --- BÚSQUEDA GLOBAL ---
        String term = (q == null) ? "" : q.trim().toLowerCase();
        if (!term.isEmpty()) {
            all = all.stream()
                    .filter(p -> p.name != null && p.name.toLowerCase().contains(term))
                    .toList();
        }

        // --- FILTRO POR TIPOS (multi) ---
        List<String> selectedTypes = new ArrayList<>();
        if (typesCsv != null && !typesCsv.isBlank()) {
            for (String t : typesCsv.split(",")) {
                String v = t.trim().toLowerCase();
                if (!v.isBlank()) {
                    selectedTypes.add(v);
                }
            }
        }
        if (!selectedTypes.isEmpty()) {
            Set<String> wanted = new HashSet<>(selectedTypes);
            all = all.stream()
                    .filter(p -> p.types != null && p.types.stream().anyMatch(t -> wanted.contains(t.toLowerCase())))
                    .toList();
        }

        // --- NUEVO: FILTRO POR LOC (región derivada de generation) ---
        if (loc != null && !loc.isBlank()) {
            String wantedLoc = loc.trim().toLowerCase();
            all = all.stream()
                    .filter(p -> p.generation != null && p.generation.equalsIgnoreCase(wantedLoc))
                    .toList();
        }

        // --- NUEVO: FILTRO POR HABITAT ---
        if (hab != null && !hab.isBlank()) {
            String wantedHab = hab.trim().toLowerCase();
            all = all.stream()
                    .filter(p -> p.habitat != null && p.habitat.equalsIgnoreCase(wantedHab))
                    .toList();
        }
        
        // // --- NUEVO: FILTRO POR HABITAT ---

        // --- PAGINACIÓN ---
        int currentPage = Math.max(1, page);
        int pageSize = Math.max(1, size);
        int total = all.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int from = Math.min((currentPage - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<PokemonCardDTO> pageItems = (from < to) ? all.subList(from, to) : List.of();

        // Adaptación a tu plantilla (Map)
        List<Map<String, Object>> cards = pageItems.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id);
            m.put("name", c.name);
            m.put("image", c.image);
            m.put("types", c.types != null ? c.types : List.of());
            Map<String, Integer> stats = new LinkedHashMap<>();
            if (c.stats != null) {
                stats.put("hp", c.stats.getOrDefault("hp", 0));
                stats.put("attack", c.stats.getOrDefault("attack", 0));
                stats.put("defense", c.stats.getOrDefault("defense", 0));
                stats.put("speed", c.stats.getOrDefault("speed", 0));
            } else {
                stats.put("hp", 0);
                stats.put("attack", 0);
                stats.put("defense", 0);
                stats.put("speed", 0);
            }
            m.put("stats", stats);
            m.put("heightM", c.heightM);
            m.put("weightKg", c.weightKg);
            m.put("baseExp", c.baseExp);
            m.put("habitat", c.habitat);         // <— para data-attrs o UI
            m.put("generation", c.generation);   // <— para data-attrs o UI
            return m;
        }).toList();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
                ? authentication.getName() : null;

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_General"); // Si no hay rol, asignamos ROLE_General

        List<String> typesAll = svc.allTypes().stream().sorted().toList();

        Usuario usuario = iRepositoryUsuario.findByUsername(username);

        model.addAttribute("usuario", usuario);
        model.addAttribute("role", role);

        // Crear lista de IDs de Pokémon favoritos para la UI
        List<Integer> favoritosIds = (usuario != null)
                ? usuario.getFavoritos().stream()
                        .map(Favoritos::getIdPokemon)
                        .toList()
                : List.of();
        model.addAttribute("favoritosIds", favoritosIds);

        // Modelo
        model.addAttribute("username", username);
        model.addAttribute("pokemones", cards);
        model.addAttribute("page", currentPage);
        model.addAttribute("size", pageSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("count", total);
        model.addAttribute("typesAll", typesAll);

        // estado de filtros para la UI
        model.addAttribute("q", term);
        model.addAttribute("selectedTypes", selectedTypes);
        model.addAttribute("typesQuery", String.join(",", selectedTypes));
        model.addAttribute("loc", (loc == null ? "" : loc));
        model.addAttribute("hab", (hab == null ? "" : hab));

        return "PokemonIndex";
    }

    /* ================== DETALLE ================== */
    @GetMapping("/{idPokemon}")
    public String getById(Model model, @PathVariable int idPokemon) {
        // dispara warmup si hace falta
        svc.warmupAsync(false);
        
        //Autenticacion
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        String username = (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
                ? authentication.getName() : null;

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_General"); // Si no hay rol, asignamos ROLE_General

        Result<PokemonFullDTO> r = svc.getById(idPokemon);

        // si aún no está el objeto en memoria -> carga
        if (r.loading || r.object == null) {
            pushLoading(model);
            // Para cuando salgas de loading, que te mande al detalle concreto
            model.addAttribute("redirectTo", "/pokemon/" + idPokemon);
            return "PokemonLoading";
        }

        Map<String, Object> p = toDetailMap(r.object);
        model.addAttribute("p", p);
        
        model.addAttribute("role", role);
        model.addAttribute("username", username);
        return "PokemonDetail";
    }

    /* ================== STATUS JSON para polling ================== */
    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> status() {
        return svc.status(); // { warming, progress, count, lastUpdated, error }
    }

    /* ================== HELPERS ================== */
    private void pushLoading(Model model) {
        Map<String, Object> st = svc.status();
        model.addAttribute("warming", st.get("warming"));
        model.addAttribute("progress", st.get("progress"));
        model.addAttribute("count", st.get("count"));
        model.addAttribute("lastUpdated", st.get("lastUpdated"));
        model.addAttribute("error", st.get("error"));
        // redirectTo lo sobrescribe el caller si quiere otra URL
        model.addAttribute("redirectTo", st.getOrDefault("redirectTo", "/pokemon"));
    }

    private String currentUrl(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String qs = req.getQueryString();
        return (qs == null || qs.isBlank()) ? uri : (uri + "?" + qs);
    }

    private Map<String, Object> toCardMap(PokemonCardDTO c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id);
        m.put("name", c.name);
        m.put("image", c.image);
        m.put("types", c.types != null ? c.types : List.of());
        m.put("heightM", c.heightM);
        m.put("weightKg", c.weightKg);
        m.put("baseExp", c.baseExp);

        Map<String, Integer> stats = new LinkedHashMap<>();
        if (c.stats != null) {
            stats.put("hp", c.stats.getOrDefault("hp", 0));
            stats.put("attack", c.stats.getOrDefault("attack", 0));
            stats.put("defense", c.stats.getOrDefault("defense", 0));
            stats.put("speed", c.stats.getOrDefault("speed", 0));
        } else {
            stats.put("hp", 0);
            stats.put("attack", 0);
            stats.put("defense", 0);
            stats.put("speed", 0);
        }
        m.put("stats", stats);
        return m;
    }

    private Map<String, Object> toDetailMap(PokemonFullDTO d) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", d.id);
        p.put("name", d.name);
        p.put("image", d.image);
        p.put("types", d.types != null ? d.types : List.of());
        p.put("heightM", d.heightM);
        p.put("weightKg", d.weightKg);
        p.put("baseExp", d.baseExp);
        p.put("genus", d.genus);
        p.put("flavor", d.flavor);
        p.put("cry", d.cry);

      
        Map<String, Integer> stats = new LinkedHashMap<>();
        int hp = getOrZero(d.stats, "hp");
        int atk = getOrZero(d.stats, "attack");
        int def = getOrZero(d.stats, "defense");
        int spd = getOrZero(d.stats, "speed");
        int satk = d.specialAttack;
        int sdef = d.specialDefense;

        stats.put("hp", hp);
        stats.put("attack", atk);
        stats.put("defense", def);
        stats.put("speed", spd);
        stats.put("specialAttack", satk);
        stats.put("specialDefense", sdef);
        p.put("stats", stats);

        List<Map<String, Object>> abilities = (d.abilities == null ? List.<Map<String, Object>>of()
                : d.abilities.stream()
                        .map(a -> Map.<String, Object>of("name", a.name, "hidden", a.hidden))
                        .toList());
        p.put("abilities", abilities);

        List<Map<String, Object>> sprites = (d.sprites == null ? List.<Map<String, Object>>of()
                : d.sprites.stream()
                        .map(s -> Map.<String, Object>of("label", s.label, "url", s.url))
                        .toList());
        p.put("sprites", sprites);

        // ===== MOVIMIENTOS (flatten) =====
        List<Map<String, Object>> movesFlat = new ArrayList<>();
        Set<String> vgSet = new TreeSet<>();
        Set<String> lmSet = new TreeSet<>();

        if (d.moves != null) {
            for (var mv : d.moves) {
                String mName = (mv.move != null ? mv.move.name : null);
                String mUrl = (mv.move != null ? mv.move.url : null);
                if (mv.versionGroupDetails != null) {
                    for (var det : mv.versionGroupDetails) {
                        String method = (det.moveLearnMethod != null ? det.moveLearnMethod.name : "");
                        String vgroup = (det.versionGroup != null ? det.versionGroup.name : "");
                        String vgUrl = (det.versionGroup != null ? det.versionGroup.url : null);
                        Integer lvl = det.levelLearnedAt;

                        if (vgroup != null && !vgroup.isBlank()) {
                            vgSet.add(vgroup);
                        }
                        if (method != null && !method.isBlank()) {
                            lmSet.add(method);
                        }

                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name", mName);
                        row.put("url", mUrl);
                        row.put("method", method);
                        row.put("level", lvl);
                        row.put("versionGroup", vgroup);
                        row.put("versionGroupUrl", vgUrl);
                        movesFlat.add(row);
                    }
                }
            }
        }
        movesFlat.sort(Comparator.<Map<String, Object>, String>comparing(m -> String.valueOf(m.get("method")), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(m -> (Integer) (m.get("level") == null ? 999 : m.get("level")))
                .thenComparing(m -> String.valueOf(m.get("name")), String.CASE_INSENSITIVE_ORDER));

        p.put("movesFlat", movesFlat);
        p.put("versionGroups", new ArrayList<>(vgSet));
        p.put("learnMethods", new ArrayList<>(lmSet));

        return p;
    }

    private int getOrZero(Map<String, Integer> m, String k) {
        return (m == null) ? 0 : m.getOrDefault(k, 0);
    }

    //Vista de cuenta
    @GetMapping("/myAccount")
    public String myAccount(Model model,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {

        // Dispara warmup si hace falta
        svc.warmupAsync(false);

        Map<String, Object> st = svc.status();
        boolean warming = Boolean.TRUE.equals(st.get("warming"));
        int cached = ((Number) st.getOrDefault("count", 0)).intValue();
        boolean emptySnapshot = svc.allCards().isEmpty();

        if (warming || cached == 0 || emptySnapshot) {
            pushLoading(model);
            model.addAttribute("redirectTo", "/myAccount");
            return "PokemonLoading";
        }

        // Obtener usuario autenticado
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
                ? authentication.getName() : null;
        
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_General"); // Si no hay rol, asignamos ROLE_General


        Usuario usuario = iRepositoryUsuario.findByUsername(username);
        List<Integer> favoritosIds = (usuario != null)
                ? usuario.getFavoritos().stream()
                        .map(Favoritos::getIdPokemon)
                        .toList()
                : List.of();

        // Traer todas las cartas y filtrar por favoritos
        List<PokemonCardDTO> all = svc.allCards().stream()
                .filter(p -> favoritosIds.contains(p.id))
                .toList();

        // --- PAGINACIÓN ---
        int currentPage = Math.max(1, page);
        int pageSize = Math.max(1, size);
        int total = all.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int from = Math.min((currentPage - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<PokemonCardDTO> pageItems = (from < to) ? all.subList(from, to) : List.of();

        // Adaptación a Map para la plantilla
        List<Map<String, Object>> cards = pageItems.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id);
            m.put("name", c.name);
            m.put("image", c.image);
            m.put("types", c.types != null ? c.types : List.of());
            Map<String, Integer> stats = new LinkedHashMap<>();
            if (c.stats != null) {
                stats.put("hp", c.stats.getOrDefault("hp", 0));
                stats.put("attack", c.stats.getOrDefault("attack", 0));
                stats.put("defense", c.stats.getOrDefault("defense", 0));
                stats.put("speed", c.stats.getOrDefault("speed", 0));
            } else {
                stats.put("hp", 0);
                stats.put("attack", 0);
                stats.put("defense", 0);
                stats.put("speed", 0);
            }
            m.put("stats", stats);
            m.put("heightM", c.heightM);
            m.put("weightKg", c.weightKg);
            m.put("baseExp", c.baseExp);
            m.put("habitat", c.habitat);
            m.put("generation", c.generation);
            return m;
        }).toList();

        model.addAttribute("usuario", usuario);
        model.addAttribute("role", role);

        model.addAttribute("username", username);
        model.addAttribute("pokemones", cards);
        model.addAttribute("page", currentPage);
        model.addAttribute("size", pageSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("count", total);
        model.addAttribute("favoritosIds", favoritosIds);

        return "PokemonesFavs";
    }

//    @GetMapping("/totalPokemonesFavotitos")
//    public String TotalPokemonesFavoritos(Model model) {
//        
//        Result result = usuarioService.GetAllPokemonesFavoritos();
//        return "TotalPokemonesFavoritos";
//    }
    @GetMapping("/totalPokemonesFavoritos")
    public String TotalPokemonesFavoritos(Model model,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        
        // Obtener usuario autenticado
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
                ? authentication.getName() : null;
        
        //Sacar Rol
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_General"); // Si no hay rol, asignamos ROLE_General
        
        if (!role.equals("ROLE_Administrador")) {
            return "Prohibition";
        }

        // Dispara warmup si hace falta
        svc.warmupAsync(false);

        Map<String, Object> st = svc.status();
        boolean warming = Boolean.TRUE.equals(st.get("warming"));
        int cached = ((Number) st.getOrDefault("count", 0)).intValue();
        boolean emptySnapshot = svc.allCards().isEmpty();

        if (warming || cached == 0 || emptySnapshot) {
            pushLoading(model);
            model.addAttribute("redirectTo", "/myAccount");
            return "PokemonLoading";
        }

        // --- OBTENER FAVORITOS ÚNICOS ---
        Result result = usuarioService.GetAllPokemonesFavoritos();
        List<Favoritos> unicos;

        if (result != null && result.correct && result.object != null) {
            unicos = (List<Favoritos>) result.object;
        } else {
            unicos = new ArrayList<>();
        }

        // --- FILTRAR CARTAS DE POKÉMON POR FAVORITOS ÚNICOS ---
        List<PokemonCardDTO> favoritosCards = svc.allCards().stream()
                .filter(p -> unicos.stream().anyMatch(f -> f.getIdPokemon() == p.id))
                .toList();
        
        // GRAFICA
         // Obtener todos los favoritos (duplicados incluidos)
        List<Favoritos> todosFavoritos = iRepositoryFavorito.findAll();
        
        //Tambien me sirve para la card
        // Mapear idPokemon -> cantidad de veces que fue favorito
        Map<Integer, Long> favoritosCount = todosFavoritos.stream()
                .collect(Collectors.groupingBy(Favoritos::getIdPokemon, Collectors.counting()));

        // Mapear idPokemon -> nombre del Pokémon usando svc.allCards()
        Map<Integer, String> idToName = svc.allCards().stream()
                .collect(Collectors.toMap(p -> p.id, p -> p.name));

        // Crear listas de nombres y cantidades para la gráfica
        List<String> labels = favoritosCount.keySet().stream()
                .map(id -> idToName.getOrDefault(id, "Desconocido"))
                .toList();

        List<Long> data = favoritosCount.values().stream().toList();

        model.addAttribute("favoritosLabels", labels); // nombres
        model.addAttribute("favoritosData", data);     // cantidad de veces que fue favorito

        // --- PAGINACIÓN ---
        int currentPage = Math.max(1, page);
        int pageSize = Math.max(1, size);
        int total = favoritosCards.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int from = Math.min((currentPage - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<PokemonCardDTO> pageItems = (from < to) ? favoritosCards.subList(from, to) : List.of();

        // Adaptación a Map para la plantilla
        List<Map<String, Object>> cards = pageItems.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id);
            m.put("name", c.name);
            m.put("image", c.image);
            m.put("types", c.types != null ? c.types : List.of());
            Map<String, Integer> stats = new LinkedHashMap<>();
            if (c.stats != null) {
                stats.put("hp", c.stats.getOrDefault("hp", 0));
                stats.put("attack", c.stats.getOrDefault("attack", 0));
                stats.put("defense", c.stats.getOrDefault("defense", 0));
                stats.put("speed", c.stats.getOrDefault("speed", 0));
            } else {
                stats.put("hp", 0);
                stats.put("attack", 0);
                stats.put("defense", 0);
                stats.put("speed", 0);
            }
            m.put("stats", stats);
            m.put("heightM", c.heightM);
            m.put("weightKg", c.weightKg);
            m.put("baseExp", c.baseExp);
            m.put("habitat", c.habitat);
            m.put("generation", c.generation);
            
            //Mostrar el total de veces que ha sido agregado a favoritos
            
            long totalFav = favoritosCount.getOrDefault(c.id, 0L);
            m.put("totalFavoritos", (int) totalFav);
            return m;
        }).toList();

        //model.addAttribute("usuario", usuario);
        model.addAttribute("username", username);
        model.addAttribute("role", role);
        model.addAttribute("pokemones", cards);
        model.addAttribute("page", currentPage);
        model.addAttribute("size", pageSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("count", total);
        //model.addAttribute("favoritosIds", favoritosIds);

        return "TotalPokemonesFavoritos";
    }

}
