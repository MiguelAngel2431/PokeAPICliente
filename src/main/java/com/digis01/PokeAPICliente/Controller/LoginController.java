
package com.digis01.PokeAPICliente.Controller;

import com.digis01.PokeAPICliente.ML.Usuario;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("login")
public class LoginController {
    
    private final AuthenticationManager authenticationManager;

    public LoginController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }
    
    
   
    @GetMapping
    public String Login(Model model) {
        return "LoginForm";
    }
    
//    @PostMapping
//    public String ProcesarLogin(@ModelAttribute("Usuario") Usuario usuario, Model model) {
//        try {
//            // Autenticación con Spring Security
//            Authentication auth = authenticationManager.authenticate(
//                new UsernamePasswordAuthenticationToken(
//                    usuario.getUsername(), 
//                    usuario.getPassword()
//                )
//            );
//
//            // Si llega aquí, la autenticación fue exitosa
//            return "redirect:/pokemon";
//
//        } catch (AuthenticationException e) {
//            // Si falla la autenticación
//            model.addAttribute("error", "Usuario o contraseña incorrectos");
//            return "LoginForm";
//        }
//    }
    
}
