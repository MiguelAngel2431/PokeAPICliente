package com.digis01.PokeAPICliente.Service;

import com.digis01.PokeAPICliente.DAO.IRepositoryFavorito;
import com.digis01.PokeAPICliente.DAO.IRepositoryUsuario;
import com.digis01.PokeAPICliente.JPA.Favoritos;
import com.digis01.PokeAPICliente.JPA.Rol;
import com.digis01.PokeAPICliente.JPA.Usuario;
import com.digis01.PokeAPICliente.ML.Result;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

@Service
public class UsuarioService implements UserDetailsService {

    private final PasswordEncoder passwordEncoder;

    private final IRepositoryUsuario iRepositoryUsuario;

    private final IRepositoryFavorito iRepositoryFavorito;

    public UsuarioService(PasswordEncoder passwordEncoder, IRepositoryUsuario iRepositoryUsuario, IRepositoryFavorito iRepositoryFavorito) {
        this.passwordEncoder = passwordEncoder;
        this.iRepositoryUsuario = iRepositoryUsuario;
        this.iRepositoryFavorito = iRepositoryFavorito;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = iRepositoryUsuario.findByUsername(username);

        if (usuario == null) {
            throw new UsernameNotFoundException("Usuario no encontrado: " + username);
        }
        
        String roleName = "ROLE_" + usuario.Rol.getNombre();

        return new User(
                usuario.getUsername(),
                usuario.getPassword(),
                List.of(new SimpleGrantedAuthority(roleName))
        );
    }

    public Result Add(@RequestBody Usuario usuario) {

        Result result = new Result();

        try {

            String passwordEncriptada = passwordEncoder.encode(usuario.getPassword());
            usuario.setPassword(passwordEncriptada);
            usuario.Rol = new Rol();
            usuario.Rol.setIdRol(2);

            Usuario savedUser = iRepositoryUsuario.save(usuario);

            result.object = savedUser;
            result.correct = true;

        } catch (Exception ex) {
            result.correct = false;
        }

        return result;
    }

    public Result AddFavorites(Favoritos pokemonFavorito) {

        Result result = new Result();

        try {

            Optional<Usuario> usuario = iRepositoryUsuario.findById(pokemonFavorito.getUsuario().getIdUsuario());

            if (usuario.isPresent()) {

                Favoritos favorito = new Favoritos();
                favorito.setIdPokemon(pokemonFavorito.getIdPokemon());

                favorito.setUsuario(usuario.get());

                Favoritos savedFavorito = iRepositoryFavorito.save(favorito);

                result.correct = true;
                result.object = savedFavorito;

            }

        } catch (Exception ex) {
            result.correct = false;
        }

        return result;

    }

    public Result RemoveFavorite(Favoritos pokemonFavorito) {

        Result result = new Result();

        try {

            // Buscar en todos los favoritos (pequeño dataset) y filtrar
            Favoritos favorito = iRepositoryFavorito.findAll().stream()
                    .filter(f -> f.getIdPokemon() == pokemonFavorito.getIdPokemon()
                    && f.getUsuario().getIdUsuario() == pokemonFavorito.getUsuario().getIdUsuario())
                    .findFirst()
                    .orElse(null);

            if (favorito != null) {
                iRepositoryFavorito.delete(favorito);
                result.correct = true;
            } else {
                result.correct = false;
            }

        } catch (Exception ex) {
            result.correct = false;
        }

        return result;

    }

}
