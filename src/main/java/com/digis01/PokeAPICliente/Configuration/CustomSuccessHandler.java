
package com.digis01.PokeAPICliente.Configuration;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                        HttpServletResponse response, 
                                        Authentication authentication) throws IOException, ServletException {
        
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        
        String redirectURL = request.getContextPath();
        
        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();
            
            if (role.equals("ROLE_Administrador")) {
                redirectURL = "/usuario/list";
                break;
            } else if(role.equals("ROLE_General")) {
                redirectURL = "/pokemon";
                break;
            }
        }
        
        response.sendRedirect(redirectURL);
        
    }

}
