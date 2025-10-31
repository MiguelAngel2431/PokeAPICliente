
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




    
    @GetMapping("/{idPokemon}")
    public String GetById(Model model, @PathVariable int idPokemon) {
        
        RestTemplate restTemplate = new RestTemplate();
        
        String url = "https://pokeapi.co/api/v2/pokemon/" + idPokemon;
        
        ResponseEntity<Map> responseEntity = restTemplate.getForEntity(url, Map.class);
        
        Map<String, Object> responseBody = responseEntity.getBody();
        
        List<Map<String, Object>> detallePokemon = (List<Map<String, Object>>) responseBody.get("results");
        
        if (responseEntity.getStatusCode() == HttpStatus.OK) {

            model.addAttribute("detallePokemon", detallePokemon);
        }
        
        return "PokemonDetail";
        
    }
    
    
    
}
