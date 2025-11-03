
package com.digis01.PokeAPICliente.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("login")
public class LoginController {
    
    @GetMapping
    public String Login(Model model) {
        
        return "LoginForm";
        
    }
    
//    @PostMapping
//    public String ProcesarLogin(@ModelAttribute("Usuario") Usuario usuario, Model model) {
//        
//        return null;
//        
//    }
    
}
