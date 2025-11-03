
package com.digis01.PokeAPICliente.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("prueba")
public class ControllerPrueba {
    
    @GetMapping
    public String Index(Model model) {
        
        return "RegisterForm";
        
    }
    
}
