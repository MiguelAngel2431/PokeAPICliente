package com.digis01.PokeAPICliente.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;

@RequestMapping("pokemon")
@Controller
public class PokemonController {

    @GetMapping
    public String Index(Model model) {

        RestTemplate restTemplate = new RestTemplate();

        String url = "https://pokeapi.co/api/v2/pokemon";

        ResponseEntity<Map> responseEntity = restTemplate.getForEntity(url, Map.class);

        Map<String, Object> responseBody = responseEntity.getBody();

        List<Map<String, Object>> pokemones = (List<Map<String, Object>>) responseBody.get("results");
        List<Map<String, Object>> pokemonesDetail = new ArrayList<>();

        if (pokemones != null) {
            for (Map<String, Object> pokemon : pokemones) {

                String name = (String) pokemon.get("name");
                String detailUrl = (String) pokemon.get("url"); // URL de detalle

                // SEGUNDA PETICIÓN: obtener la imagen de cada Pokémon
                Map<String, Object> detalle = restTemplate.getForObject(detailUrl, Map.class);
                String imageUrl = "";
                if (detalle != null && detalle.containsKey("sprites")) {
                    Map<String, Object> sprites = (Map<String, Object>) detalle.get("sprites");
                    imageUrl = (String) sprites.get("front_default");
                }

                pokemonesDetail.add(Map.of(
                        "name", name,
                        "image", imageUrl != null ? imageUrl : ""
                ));

            }

        }

        if (responseEntity.getStatusCode() == HttpStatus.OK) {

            model.addAttribute("pokemones", pokemonesDetail);
        }

        return "PokemonIndex";

    }

}
