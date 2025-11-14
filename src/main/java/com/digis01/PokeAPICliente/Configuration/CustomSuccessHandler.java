
package com.digis01.PokeAPICliente.Configuration;

import com.digis01.PokeAPICliente.DAO.IRepositoryUsuario;
import com.digis01.PokeAPICliente.JPA.Usuario;
import com.digis01.PokeAPICliente.Service.ServiceEmail;
import com.digis01.PokeAPICliente.Service.TokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private ServiceEmail serviceEmail;
    
    @Autowired
    private IRepositoryUsuario iRepositoryUsuario;
    
    @Autowired
    private TokenService tokenService;
    
//    @Override
//    public void onAuthenticationSuccess(HttpServletRequest request, 
//                                        HttpServletResponse response, 
//                                        Authentication authentication) throws IOException, ServletException {
//        
//        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
//        
//        String redirectURL = request.getContextPath();
//        
//        for (GrantedAuthority authority : authorities) {
//            String role = authority.getAuthority();
//            
//            if (role.equals("ROLE_Administrador")) {
//                redirectURL = "/usuario/list";
//                break;
//            } else if(role.equals("ROLE_General")) {
//                redirectURL = "/pokemon";
//                break;
//            }
//        }
//        
//        response.sendRedirect(redirectURL);
//        
//    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                        HttpServletResponse response, 
                                        Authentication authentication) throws IOException, ServletException {
        
        String username = authentication.getName();
        Usuario usuario = iRepositoryUsuario.findByUsername(username);
        
        String token = tokenService.generateVerificationToken(usuario.getEmail());
        
        String url = "http://localhost:8080/login/confirm?token=" + token;
        
        //Enviamos correo
        serviceEmail.sendLoginVerificationEmail(usuario.getEmail(), usuario.getNombre(), url);
        
        //Invalida la sesion, todavia NO inicia
        request.getSession().invalidate();
        
        //Mostrar pantalla de "Revisa tu correo"
        response.sendRedirect("/login/validacion-enviada");
    }

}
