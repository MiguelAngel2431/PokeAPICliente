
package com.digis01.PokeAPICliente.RestController;

import com.digis01.PokeAPICliente.JPA.Favoritos;
import com.digis01.PokeAPICliente.JPA.Usuario;
import com.digis01.PokeAPICliente.ML.Result;
import com.digis01.PokeAPICliente.Service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    
    @GetMapping()
    public Result GetAll() {
        return usuarioService.GetAll();
    }
    
    @GetMapping("/{IdUsuario}")
    public Result GetById(@PathVariable int IdUsuario) {
        return usuarioService.GetById(IdUsuario);
    }
    
    @GetMapping("/GetByUsername/{Username}")
    public Result GetByUsername(@PathVariable String Username) {
        return usuarioService.GetByUsername(Username);
    }
    
    @DeleteMapping("/{IdUsuario}")
    public Result Delete(@PathVariable int IdUsuario) {
        return usuarioService.Delete(IdUsuario);
    }
    
    @PatchMapping("/{IdUsuario}")
    public Result Update(@RequestBody Usuario usuario) {
        return usuarioService.Update(usuario);
    }
    
}
