
package com.digis01.PokeAPICliente.Service;

import com.digis01.PokeAPICliente.DAO.IRepositoryUsuario;
import com.digis01.PokeAPICliente.JPA.Rol;
import com.digis01.PokeAPICliente.JPA.Usuario;
import com.digis01.PokeAPICliente.ML.Result;
import java.util.List;
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

    public UsuarioService(PasswordEncoder passwordEncoder, IRepositoryUsuario iRepositoryUsuario) {
        this.passwordEncoder = passwordEncoder;
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
    
    
    
    
    
}
