package com.digis01.PokeAPICliente.Controller;

import com.digis01.PokeAPICliente.ML.Result;
import com.digis01.PokeAPICliente.Service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuario")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public String mostrarForm(Model model) {
        if (!model.containsAttribute("Usuario")) {
            model.addAttribute("Usuario", new com.digis01.PokeAPICliente.JPA.Usuario());
        }
        return "RegisterForm";
    }

    /* ============ ALTA ============ */
    @PostMapping("/add")
    public String add(
            @ModelAttribute("Usuario") com.digis01.PokeAPICliente.JPA.Usuario usuario,
            Model model, RedirectAttributes redirectAttributes) {

        Result result = usuarioService.Add(usuario);

        if (result != null && result.correct) {
            redirectAttributes.addFlashAttribute("mensaje", "Registro exitoso. ¡Ahora inicia sesión!");
            return "redirect:/login"; 
        } else {
            model.addAttribute("mensajeError",
                    (result != null && result.errorMessage != null)
                            ? result.errorMessage
                            : "No se pudo registrar el usuario.");

            model.addAttribute("Usuario", usuario);
            return "RegisterForm";
        }
    }

    /* ============ LISTAR ============ */
    @GetMapping("/list")
    public String getAll(Model model,
                         @RequestParam(value = "msg", required = false) String msg) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
                ? authentication.getName()
                : null;

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_General");

        if (!"ROLE_Administrador".equals(role)) {
            return "Prohibition";
        }

        model.addAttribute("username", username);
        model.addAttribute("role", role);

        Result result = usuarioService.GetAll();
        List<?> usuarios = Collections.emptyList();
        if (result != null && result.correct && result.object instanceof List<?>) {
            usuarios = (List<?>) result.object;
        }
        model.addAttribute("usuarios", usuarios);
        if (msg != null) {
            model.addAttribute("mensaje", msg);
        }
        return "UsuarioIndex";
    }

    /* ============ DETALLE POR ID (VER) ============ */
    @GetMapping("/{id}")
    public String getById(@PathVariable("id") Integer id, Model model) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
                ? authentication.getName()
                : null;

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_General");

        if (!"ROLE_Administrador".equals(role)) {
            return "Prohibition";
        }

        Result result = usuarioService.GetById(id);

        com.digis01.PokeAPICliente.JPA.Usuario found = null;
        if (result != null && result.correct && result.object != null) {
            Object obj = result.object;

            if (obj instanceof com.digis01.PokeAPICliente.JPA.Usuario u) {
                found = u;
            } else if (obj instanceof Optional<?> opt && opt.isPresent()
                    && opt.get() instanceof com.digis01.PokeAPICliente.JPA.Usuario u2) {
                found = u2;
            }
        }

        if (found == null) {
            model.addAttribute("mensajeError", "Usuario no encontrado");
        } else {
            model.addAttribute("usuario", found);
        }
        model.addAttribute("username", username);
        model.addAttribute("role", role);
        return "UsuarioDetalle";
    }

    /* ============ BUSCAR POR USERNAME (para offcanvas) ============ */
    @GetMapping("/by-username")
    public String getByUsername(@RequestParam("u") String username,
                                HttpServletRequest request,
                                Model model) {

        Result result = usuarioService.GetByUsername(username);

        com.digis01.PokeAPICliente.JPA.Usuario found = null;
        if (result != null && result.correct && result.object != null) {
            Object obj = result.object;
            if (obj instanceof com.digis01.PokeAPICliente.JPA.Usuario u) {
                found = u;
            } else if (obj instanceof Optional<?> opt && opt.isPresent()
                    && opt.get() instanceof com.digis01.PokeAPICliente.JPA.Usuario u2) {
                found = u2;
            }
        }

        if (found == null) {
            model.addAttribute("mensajeError", "No se encontró el usuario con username: " + username);
        } else {
            model.addAttribute("usuario", found);
        }

        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
        return isAjax ? "UsuarioDetalle :: content" : "UsuarioDetalle";
    }

    /* ============ ELIMINAR ============ */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") Integer id, Model model, RedirectAttributes redirectAttributes) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
                ? authentication.getName()
                : null;

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_General");

        if (!"ROLE_Administrador".equals(role)) {
            return "Prohibition";
        }

        Result result = usuarioService.Delete(id);
        String msg = (result != null && result.correct)
                ? "Usuario eliminado correctamente"
                : "No se pudo eliminar el usuario";

        redirectAttributes.addFlashAttribute("mensaje", msg);
        model.addAttribute("username", username);
        model.addAttribute("role", role);

        Result listResult = usuarioService.GetAll();
        List<?> usuarios = Collections.emptyList();
        if (listResult != null && listResult.correct && listResult.object instanceof List<?>) {
            usuarios = (List<?>) listResult.object;
        }
        model.addAttribute("usuarios", usuarios);

        return "UsuarioIndex";
    }

    /* ============ EDITAR: FORM ============ */
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Integer id, Model model) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
                ? authentication.getName()
                : null;

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_General");

        if (!"ROLE_Administrador".equals(role)) {
            return "Prohibition";
        }

        Result result = usuarioService.GetById(id);

        com.digis01.PokeAPICliente.JPA.Usuario found = null;
        if (result != null && result.correct && result.object != null) {
            Object object = result.object;
            if (object instanceof com.digis01.PokeAPICliente.JPA.Usuario usuario) {
                found = usuario;
            } else if (object instanceof Optional<?> optional && optional.isPresent()
                    && optional.get() instanceof com.digis01.PokeAPICliente.JPA.Usuario u2) {
                found = u2;
            }
        }

        if (found == null) {
            model.addAttribute("mensajeError", "Usuario no encontrado.");
            model.addAttribute("username", username);
            model.addAttribute("role", role);

            Result listResult = usuarioService.GetAll();
            List<?> usuarios = Collections.emptyList();
            if (listResult != null && listResult.correct && listResult.object instanceof List<?>) {
                usuarios = (List<?>) listResult.object;
            }
            model.addAttribute("usuarios", usuarios);

            return "UsuarioIndex";
        }

        model.addAttribute("Usuario", found);
        model.addAttribute("username", username);
        model.addAttribute("role", role);
        return "UsuarioEdit";
    }

    /* ============ EDITAR: POST ============ */
    @PostMapping("/edit/{id}")
    public String update(@PathVariable("id") Integer id,
                         @ModelAttribute("Usuario") com.digis01.PokeAPICliente.JPA.Usuario usuario,
                         Model model) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (authentication != null && !(authentication instanceof AnonymousAuthenticationToken))
                ? authentication.getName()
                : null;

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_General");

        if (!"ROLE_Administrador".equals(role)) {
            return "Prohibition";
        }

        usuario.setIdUsuario(id);
        Result result = usuarioService.Update(usuario);

        if (result != null && result.correct) {
            model.addAttribute("mensaje", "Usuario actualizado correctamente.");
            model.addAttribute("username", username);
            model.addAttribute("role", role);

            Result listResult = usuarioService.GetAll();
            List<?> usuarios = Collections.emptyList();
            if (listResult != null && listResult.correct && listResult.object instanceof List<?>) {
                usuarios = (List<?>) listResult.object;
            }
            model.addAttribute("usuarios", usuarios);

            return "UsuarioIndex";
        } else {
            model.addAttribute("mensajeError",
                    (result != null && result.errorMessage != null)
                            ? result.errorMessage
                            : "No se pudo actualizar el usuario.");
            model.addAttribute("Usuario", usuario);
            model.addAttribute("username", username);
            model.addAttribute("role", role);
            return "UsuarioEdit";
        }
    }
}
