package com.digis01.PokeAPICliente.Controller;

import com.digis01.PokeAPICliente.JPA.Usuario;
import com.digis01.PokeAPICliente.Jwt.JwtUtil;
import com.digis01.PokeAPICliente.Service.UsuarioService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
@RequestMapping("/api/auth")
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final UsuarioService usuarioService;

    public AuthController(AuthenticationManager authenticationManager, UsuarioService usuarioService) {
        this.authenticationManager = authenticationManager;
        this.usuarioService = usuarioService;
    }
    
    @PostMapping("/login")
    public String login(@RequestBody Usuario usuario) {
        
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(usuario.getUsername(), usuario.getPassword())
        );
        
        UserDetails user = usuarioService.loadUserByUsername(usuario.getUsername());
        return JwtUtil.generarToken(user.getUsername());
        
    }
    
    
    
    
    
}
