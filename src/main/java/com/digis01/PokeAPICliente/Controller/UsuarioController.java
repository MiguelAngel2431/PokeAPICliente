
package com.digis01.PokeAPICliente.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("usuario")
public class UsuarioController {
    
    @GetMapping
    public String MostrarForm(Model model) {
        return "RegisterForm";
    }
    
    @PostMapping
    public String add(@ModelAttribute("Usuario") com.digis01.PokeAPICliente.JPA.Usuario usuario,
            Model model) {
        
        return null;
        
    }
    
    
}
