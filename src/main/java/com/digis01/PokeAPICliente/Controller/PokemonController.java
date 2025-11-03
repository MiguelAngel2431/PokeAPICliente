
package com.digis01.PokeAPICliente.Controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

@RequestMapping("pokemon")
@Controller
public class PokemonController {

    @GetMapping
    public String Index(Model model,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "12") int size) {

        RestTemplate restTemplate = new RestTemplate();

        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        int offset = (currentPage - 1) * pageSize;

        String url = "https://pokeapi.co/api/v2/pokemon?offset=" + offset + "&limit=" + pageSize;
        ResponseEntity<Map> responseEntity = restTemplate.getForEntity(url, Map.class);

        if (responseEntity.getStatusCode() != HttpStatus.OK || responseEntity.getBody() == null) {
            model.addAttribute("pokemones", Collections.emptyList());
            model.addAttribute("page", 1);
            model.addAttribute("size", pageSize);
            model.addAttribute("totalPages", 1);
            model.addAttribute("count", 0);
            model.addAttribute("typesAll", Collections.emptyList());
            return "PokemonIndex";
        }

        Map<String, Object> body = responseEntity.getBody();
        int count = ((Number) body.getOrDefault("count", 0)).intValue();
        int totalPages = Math.max(1, (int) Math.ceil(count / (double) pageSize));

        List<Map<String, Object>> results =
                (List<Map<String, Object>>) body.getOrDefault("results", Collections.emptyList());

        List<Map<String, Object>> pokemonesDetail = new ArrayList<>();

        for (Map<String, Object> p : results) {
            String name = (String) p.get("name");
            String detailUrl = (String) p.get("url");

            Map<String, Object> detalle = restTemplate.getForObject(detailUrl, Map.class);
            if (detalle == null) continue;

            int id = ((Number) detalle.getOrDefault("id", 0)).intValue();
            double heightM = ((Number) detalle.getOrDefault("height", 0)).doubleValue() / 10.0;
            double weightKg = ((Number) detalle.getOrDefault("weight", 0)).doubleValue() / 10.0;
            Number baseExp = (Number) detalle.getOrDefault("base_experience", 0);

            // Imagen oficial (con respaldos)
            String imageUrl = "";
            Object spritesObj = detalle.get("sprites");
            if (spritesObj instanceof Map) {
                Map<String, Object> sprites = (Map<String, Object>) spritesObj;
                try {
                    Map<String, Object> other = (Map<String, Object>) sprites.get("other");
                    if (other != null) {
                        Map<String, Object> official = (Map<String, Object>) other.get("official-artwork");
                        if (official != null) imageUrl = (String) official.get("front_default");
                        if (imageUrl == null) {
                            Map<String, Object> dream = (Map<String, Object>) other.get("dream_world");
                            if (dream != null) imageUrl = (String) dream.get("front_default");
                        }
                    }
                    if (imageUrl == null) imageUrl = (String) sprites.get("front_default");
                } catch (ClassCastException ignored) { }
            }
            if (imageUrl == null) imageUrl = "";

            // Tipos
            List<String> types = new ArrayList<>();
            Object typesObj = detalle.get("types");
            if (typesObj instanceof List) {
                for (Object t : (List<?>) typesObj) {
                    Map<String, Object> tMap = (Map<String, Object>) t;
                    Map<String, Object> type = (Map<String, Object>) tMap.get("type");
                    if (type != null && type.get("name") != null) {
                        types.add(((String) type.get("name")));
                    }
                }
            }

            // Stats relevantes
            Map<String, Integer> statsMap = new HashMap<>();
            statsMap.put("hp", 0);
            statsMap.put("attack", 0);
            statsMap.put("defense", 0);
            statsMap.put("speed", 0);

            Object statsObj = detalle.get("stats");
            if (statsObj instanceof List) {
                for (Object s : (List<?>) statsObj) {
                    Map<String, Object> sMap = (Map<String, Object>) s;
                    Number base = (Number) sMap.getOrDefault("base_stat", 0);
                    Map<String, Object> stat = (Map<String, Object>) sMap.get("stat");
                    if (stat == null) continue;
                    String statName = (String) stat.get("name");
                    if (statName == null) continue;
                    switch (statName) {
                        case "hp" -> statsMap.put("hp", base.intValue());
                        case "attack" -> statsMap.put("attack", base.intValue());
                        case "defense" -> statsMap.put("defense", base.intValue());
                        case "speed" -> statsMap.put("speed", base.intValue());
                    }
                }
            }

            Map<String, Object> card = new HashMap<>();
            card.put("id", id);
            card.put("name", name);
            card.put("image", imageUrl);
            card.put("types", types);
            card.put("heightM", heightM);
            card.put("weightKg", weightKg);
            card.put("baseExp", baseExp != null ? baseExp.intValue() : 0);
            card.put("stats", statsMap);

            pokemonesDetail.add(card);
        }

        // Traer lista de tipos para filtros
        List<String> typesAll = new ArrayList<>();
        try {
            ResponseEntity<Map> typeResp = restTemplate.getForEntity("https://pokeapi.co/api/v2/type", Map.class);
            if (typeResp.getStatusCode() == HttpStatus.OK && typeResp.getBody() != null) {
                List<Map<String, Object>> tresults = (List<Map<String, Object>>) typeResp.getBody().get("results");
                for (Map<String, Object> t : tresults) {
                    String n = (String) t.get("name");
                    if (n != null && !n.equals("unknown") && !n.equals("shadow")) typesAll.add(n);
                }
                Collections.sort(typesAll);
            }
        } catch (Exception ignored) { }

        model.addAttribute("pokemones", pokemonesDetail);
        model.addAttribute("page", currentPage);
        model.addAttribute("size", pageSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("count", count);
        model.addAttribute("typesAll", typesAll);

        return "PokemonIndex";
    }


// Helper para agregar sprites si existen
private void addSprite(List<Map<String,String>> sprites, String label, Object urlObj){
    if (urlObj instanceof String url && url != null && !url.isBlank()) {
        sprites.add(Map.of("label", label, "url", url));
    }
}

@GetMapping("/{idPokemon}")
public String GetById(Model model, @PathVariable int idPokemon) {

    RestTemplate restTemplate = new RestTemplate();

    Map<String, Object> d = restTemplate.getForObject("https://pokeapi.co/api/v2/pokemon/" + idPokemon, Map.class);
    if (d == null) {
        model.addAttribute("p", null);
        return "PokemonDetail";
    }

    // ===== Imagen principal (respaldo por rutas) =====
    String image = "";
    Object spritesObj = d.get("sprites");
    if (spritesObj instanceof Map) {
        Map<String, Object> sprites = (Map<String, Object>) spritesObj;
        try {
            Map<String, Object> other = (Map<String, Object>) sprites.get("other");
            if (other != null) {
                Map<String, Object> official = (Map<String, Object>) other.get("official-artwork");
                if (official != null) image = (String) official.get("front_default");
                if (image == null) {
                    Map<String, Object> home = (Map<String, Object>) other.get("home");
                    if (home != null) image = (String) home.get("front_default");
                }
                if (image == null) {
                    Map<String, Object> dream = (Map<String, Object>) other.get("dream_world");
                    if (dream != null) image = (String) dream.get("front_default");
                }
            }
            if (image == null) image = (String) sprites.get("front_default");
        } catch (ClassCastException ignored) {}
    }
    if (image == null) image = "";

    // ===== Tipos =====
    List<String> types = new ArrayList<>();
    Object typesObj = d.get("types");
    if (typesObj instanceof List) {
        for (Object t : (List<?>) typesObj) {
            Map<String, Object> tMap = (Map<String, Object>) t;
            Map<String, Object> type = (Map<String, Object>) tMap.get("type");
            if (type != null && type.get("name") != null) types.add((String) type.get("name"));
        }
    }

    // ===== Stats =====
    Map<String, Integer> stats = new HashMap<>();
    stats.put("hp", 0);
    stats.put("attack", 0);
    stats.put("defense", 0);
    stats.put("specialAttack", 0);
    stats.put("specialDefense", 0);
    stats.put("speed", 0);

    Object statsObj = d.get("stats");
    if (statsObj instanceof List) {
        for (Object s : (List<?>) statsObj) {
            Map<String, Object> sMap = (Map<String, Object>) s;
            Number base = (Number) sMap.getOrDefault("base_stat", 0);
            Map<String, Object> stat = (Map<String, Object>) sMap.get("stat");
            if (stat == null) continue;
            String n = (String) stat.get("name");
            if (n == null) continue;
            switch (n) {
                case "hp" -> stats.put("hp", base.intValue());
                case "attack" -> stats.put("attack", base.intValue());
                case "defense" -> stats.put("defense", base.intValue());
                case "special-attack" -> stats.put("specialAttack", base.intValue());
                case "special-defense" -> stats.put("specialDefense", base.intValue());
                case "speed" -> stats.put("speed", base.intValue());
            }
        }
    }

    // ===== Habilidades (con hidden) =====
    List<Map<String, Object>> abilities = new ArrayList<>();
    Object abilitiesObj = d.get("abilities");
    if (abilitiesObj instanceof List) {
        for (Object a : (List<?>) abilitiesObj) {
            Map<String, Object> aMap = (Map<String, Object>) a;
            Map<String, Object> ability = (Map<String, Object>) aMap.get("ability");
            boolean hidden = Boolean.TRUE.equals(aMap.get("is_hidden"));
            if (ability != null && ability.get("name") != null) {
                abilities.add(Map.of(
                        "name", (String) ability.get("name"),
                        "hidden", hidden
                ));
            }
        }
    }

    // ===== Cries (audio) =====
    String cry = null;
    try {
        Map<String, Object> cries = (Map<String, Object>) d.get("cries");
        if (cries != null) {
            cry = (String) (cries.get("latest") != null ? cries.get("latest") : cries.get("legacy"));
        }
    } catch (Exception ignored) {}

    // ===== Sprites (para carrusel) =====
    List<Map<String, Object>> sprites = new ArrayList<>();
    try {
        Map<String, Object> s = (Map<String, Object>) d.get("sprites");
        Map<String, Object> other = s != null ? (Map<String, Object>) s.get("other") : null;
        Map<String, Object> official = other != null ? (Map<String, Object>) other.get("official-artwork") : null;
        Map<String, Object> home = other != null ? (Map<String, Object>) other.get("home") : null;
        Map<String, Object> dream = other != null ? (Map<String, Object>) other.get("dream_world") : null;
        Map<String, Object> showdown = other != null ? (Map<String, Object>) other.get("showdown") : null;

        // helper
        java.util.function.BiConsumer<String,String> add = (url,label) -> {
            if (url != null && !url.isBlank()) sprites.add(Map.of("url", url, "label", label));
        };

        // Orden propuesto
        add.accept(official != null ? (String) official.get("front_default") : null, "Artwork");
        add.accept(home != null ? (String) home.get("front_default") : null, "Home");
        add.accept(home != null ? (String) home.get("front_shiny") : null, "Home Shiny");
        if (showdown != null) {
            add.accept((String) showdown.get("front_default"), "Showdown Front");
            add.accept((String) showdown.get("back_default"), "Showdown Back");
            add.accept((String) showdown.get("front_shiny"), "Showdown Shiny");
        }
        add.accept(s != null ? (String) s.get("front_default") : null, "Front");
        add.accept(s != null ? (String) s.get("front_shiny") : null, "Front Shiny");
        add.accept(s != null ? (String) s.get("back_default") : null, "Back");
        add.accept(s != null ? (String) s.get("back_shiny") : null, "Back Shiny");
        add.accept(s != null ? (String) s.get("front_female") : null, "Front Female");
        add.accept(s != null ? (String) s.get("front_shiny_female") : null, "Front Female Shiny");
        add.accept(s != null ? (String) s.get("back_female") : null, "Back Female");
        add.accept(s != null ? (String) s.get("back_shiny_female") : null, "Back Female Shiny");
        add.accept(dream != null ? (String) dream.get("front_default") : null, "Dream World");

        if (sprites.isEmpty()) sprites.add(Map.of("url", image, "label", "Sprite"));
    } catch (Exception ignored) {}

    // ===== Species (genus + flavor) =====
    String genus = "", flavor = "";
    try {
        Map<String, Object> species = (Map<String, Object>) d.get("species");
        if (species != null && species.get("url") != null) {
            String sUrl = (String) species.get("url");
            Map<String, Object> sp = restTemplate.getForObject(sUrl, Map.class);
            if (sp != null) {
                List<Map<String, Object>> genera = (List<Map<String, Object>>) sp.get("genera");
                if (genera != null) {
                    String gEs = "", gEn = "";
                    for (Map<String, Object> g : genera) {
                        Map<String, Object> lang = (Map<String, Object>) g.get("language");
                        String ln = lang != null ? (String) lang.get("name") : "";
                        String gv = (String) g.get("genus");
                        if ("es".equals(ln)) gEs = gv; if ("en".equals(ln)) gEn = gv;
                    }
                    genus = !gEs.isEmpty() ? gEs : gEn;
                }
                List<Map<String, Object>> fts = (List<Map<String, Object>>) sp.get("flavor_text_entries");
                if (fts != null) {
                    String fEs = "", fEn = "";
                    for (Map<String, Object> ft : fts) {
                        Map<String, Object> lang = (Map<String, Object>) ft.get("language");
                        String ln = lang != null ? (String) lang.get("name") : "";
                        String txt = ((String) ft.get("flavor_text")).replaceAll("[\\n\\f]", " ");
                        if ("es".equals(ln) && fEs.isEmpty()) fEs = txt;
                        if ("en".equals(ln) && fEn.isEmpty()) fEn = txt;
                    }
                    flavor = !fEs.isEmpty() ? fEs : fEn;
                }
            }
        }
    } catch (Exception ignored) {}

    int id = ((Number) d.getOrDefault("id", idPokemon)).intValue();

    Map<String, Object> p = new HashMap<>();
    p.put("id", id);
    p.put("name", (String) d.getOrDefault("name", "pokemon"));
    p.put("image", image);
    p.put("types", types);
    p.put("heightM", ((Number) d.getOrDefault("height", 0)).doubleValue()/10.0);
    p.put("weightKg", ((Number) d.getOrDefault("weight", 0)).doubleValue()/10.0);
    p.put("baseExp", ((Number) d.getOrDefault("base_experience", 0)).intValue());
    p.put("stats", stats);
    p.put("abilities", abilities);        // << nombre + hidden
    p.put("genus", genus);
    p.put("flavor", flavor);
    p.put("sprites", sprites);            // << para carrusel
    p.put("cry", cry);                    // << audio

    model.addAttribute("p", p);
    return "PokemonDetail";
}


    
    
    
    
    
    
}
