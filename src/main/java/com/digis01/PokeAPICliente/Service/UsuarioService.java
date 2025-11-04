
package com.digis01.PokeAPICliente.Service;

import com.digis01.PokeAPICliente.DAO.IRepositoryUsuario;
import com.digis01.PokeAPICliente.JPA.Usuario;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService implements UserDetailsService {
    
    private final IRepositoryUsuario iRepositoryUsuario;

    public UsuarioService(IRepositoryUsuario iRepositoryUsuario) {
        this.iRepositoryUsuario = iRepositoryUsuario;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = iRepositoryUsuario.findByUsername(username);
        
        if (usuario == null) {
            throw new UsernameNotFoundException("Usuario no encontrado: " + username);
        }
        
        return new User(
                    usuario.getUsername(),
                    usuario.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.Rol.getNombre()))
        );
    }
    
    
    
    
    
}
