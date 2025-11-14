
package com.digis01.PokeAPICliente.Controller;

import com.digis01.PokeAPICliente.DAO.IRepositoryUsuario;
import com.digis01.PokeAPICliente.JPA.Rol;
import com.digis01.PokeAPICliente.JPA.Usuario;
import com.digis01.PokeAPICliente.Service.ServiceEmail;
import com.digis01.PokeAPICliente.Service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@Controller
@RequestMapping("login")
public class LoginController {
    
    @Autowired
    private IRepositoryUsuario iRepositoryUsuario;
    
    @Autowired
    private TokenService tokenService;
    
    @Autowired
    private ServiceEmail serviceEmail;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
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
    
    @GetMapping("/recuperarPassword")
    public String RecuperarPassword(Model model) {
        return "ResetPassword";
    }
    
    @PostMapping("/recuperarPassword")
    public String procesarResetPassword(Model model,
            @RequestParam("email") String email) {
        
        Usuario usuario = iRepositoryUsuario.findByEmail(email);
        
        if (usuario != null) {
            String token = tokenService.generateVerificationToken(email);
            serviceEmail.sendPasswordResetEmail(email, token);
            model.addAttribute("mensaje", "Se ha enviado una liga a tu correo.");
            
        } else {
            model.addAttribute("error", "El correo no esta registrado");
        }
        
        return "ResetPassword";
        
    }
    
    @GetMapping("/restablecerContrasenia")
    public String resetPassword(@RequestParam("token") String token, Model model) {

        model.addAttribute("token", token);

        return "SolicitarPasswords";

    }
    
    @PostMapping("/restablecerContrasenia")
    public String procesarResetPassword(@RequestParam("token") String token,
            @RequestParam("newPassword") String password, 
            Model model) {
        
        String email = tokenService.validateToken(token);
        
        if (email != null) {
            
            Usuario usuario = iRepositoryUsuario.findByEmail(email);
            String passwordEncriptada = passwordEncoder.encode(password);
            
            usuario.setPassword(passwordEncriptada);
            
            iRepositoryUsuario.save(usuario);
            
            serviceEmail.sendPasswordChangedNotification(email, usuario.getUsername());
            
            model.addAttribute("mensaje", "Tu contraseña se ha cambiado con exito.");
            
            return "LoginForm";
            
        } else {
            model.addAttribute("error", "El enlace no es válido o ha expirado.");
            return "ResetPassword";
        }
        
    }
    
    @GetMapping("/validacion-enviada")
    public String validacionEnviada() {
        return "Login-validacion";
    }
    
    @GetMapping("/confirm")
    public String confirmarLogin(@RequestParam("token") String token,
            HttpServletRequest request) {
        
        //Validamos token
        String email = tokenService.validateToken(token);
        
        if (email == null) {
            return "redirect:/login?error=token";
        }
        
        Usuario usuario = iRepositoryUsuario.findByEmail(email);
        
        Rol rol = usuario.getRol();
        
        String rolString = "ROLE_" + rol.getNombre();
        
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(rolString));
        
        UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(usuario.getUsername(), null, authorities);
        
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        String redirectURL = "/"; // default
        
        if (rolString.equals("ROLE_Administrador")) {
            redirectURL = "/usuario/list";
        } else if (rolString.equals("ROLE_General")) {
            redirectURL = "/pokemon";
        }
        
        return "redirect:" + redirectURL;
    }
    
}
