package com.digis01.PokeAPICliente.Controller;

import com.digis01.PokeAPICliente.ML.Result;
import com.digis01.PokeAPICliente.ML.Usuario;
import com.digis01.PokeAPICliente.Service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("usuario")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public String MostrarForm(Model model) {
        Usuario usuario = new Usuario();
        model.addAttribute("Usuario", usuario);
        return "RegisterForm";
    }

    @PostMapping("add")
    public String add(@ModelAttribute("Usuario") com.digis01.PokeAPICliente.JPA.Usuario usuario,
            Model model) {

        Result result = usuarioService.Add(usuario);

        return null;

    }

    

}
