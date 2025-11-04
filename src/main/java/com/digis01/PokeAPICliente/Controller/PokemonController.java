package com.digis01.PokeAPICliente.Controller;

import com.digis01.PokeAPICliente.DTO.PageDTO;
import com.digis01.PokeAPICliente.DTO.PokemonCardDTO;
import com.digis01.PokeAPICliente.DTO.PokemonFullDTO;
import com.digis01.PokeAPICliente.ML.Result;
import com.digis01.PokeAPICliente.Service.PokedexCacheService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RequestMapping("pokemon")
@Controller
public class PokemonController {

    private final PokedexCacheService svc;

    public PokemonController(PokedexCacheService svc) {
        this.svc = svc;
    }

    /* ================== INDEX ================== */
    @GetMapping
public String index(Model model,
                    @RequestParam(defaultValue = "1") int page,
                    @RequestParam(defaultValue = "12") int size,
                    @RequestParam(name = "q", required = false) String q) {

    svc.warmupAsync(false);

    Result<PageDTO<PokemonCardDTO>> r = (q == null || q.isBlank())
            ? svc.getIndexPage(page, size)
            : svc.searchCards(q, page, size);

    if (r.loading || r.object == null) {
        pushLoading(model);
        return "PokemonLoading";
    }

    PageDTO<PokemonCardDTO> pg = r.object;
    List<Map<String, Object>> cards = pg.items.stream().map(this::toCardMap).toList();

    model.addAttribute("pokemones", cards);
    model.addAttribute("page", pg.page);
    model.addAttribute("size", pg.size);
    model.addAttribute("totalPages", pg.totalPages);
    model.addAttribute("count", pg.total);

    // TODOS los tipos desde la caché
    model.addAttribute("typesAll", svc.getAllTypes());

    // Para que el input conserve el valor actual
    model.addAttribute("q", q == null ? "" : q);

    return "PokemonIndex";
}

    /* ================== DETALLE ================== */
    @GetMapping("/{idPokemon}")
    public String getById(Model model, @PathVariable int idPokemon) {
        // dispara warmup si hace falta
        svc.warmupAsync(false);

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
        // redirectTo se puede setear en el caller; si no existe, index
        model.addAttribute("redirectTo", st.getOrDefault("redirectTo", "/pokemon"));
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

        // Tu index usa p.stats.hp/attack/defense/speed
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

        // Tu detail.html suma total así: p.stats.hp + p.stats.attack + p.stats.defense
        // + p.stats.specialAttack + p.stats.specialDefense + p.stats.speed
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

        // abilities: [{name, hidden}]
        List<Map<String, Object>> abilities = (d.abilities == null ? List.<Map<String, Object>>of()
                : d.abilities.stream()
                        .map(a -> Map.<String, Object>of("name", a.name, "hidden", a.hidden))
                        .toList());
        p.put("abilities", abilities);

        // sprites: [{label,url}]
        List<Map<String, Object>> sprites = (d.sprites == null ? List.<Map<String, Object>>of()
                : d.sprites.stream()
                        .map(s -> Map.<String, Object>of("label", s.label, "url", s.url))
                        .toList());
        p.put("sprites", sprites);

        return p;
    }

    private int getOrZero(Map<String, Integer> m, String k) {
        return (m == null) ? 0 : m.getOrDefault(k, 0);
    }
}
