
package com.digis01.PokeAPICliente.RestController;

import com.digis01.PokeAPICliente.JPA.Favoritos;
import com.digis01.PokeAPICliente.ML.Result;
import com.digis01.PokeAPICliente.Service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuario")
public class UsuarioRestController {
    
    @Autowired
    private UsuarioService usuarioService;
    
    @PostMapping("/addFavorites")
    public Result agregarFavoritos(@RequestBody Favoritos pokemonFavorito) {
        return usuarioService.AddFavorites(pokemonFavorito);
    }
    
    @PostMapping("removeFavorites")
    public Result removerFavoritos(@RequestBody Favoritos pokemonFavorito) {
        return usuarioService.RemoveFavorite(pokemonFavorito);
    }
    
    @GetMapping("totalPokemonesFavoritos")
    public Result totalPokemonesFavoritos() {
        return usuarioService.GetAllPokemonesFavoritos();
    }
    
    
}
