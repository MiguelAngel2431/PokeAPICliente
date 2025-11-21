
package com.digis01.PokeAPICliente.Configuration;

import com.digis01.PokeAPICliente.Service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
//    private final UsuarioService usuarioService;
//
//    public SecurityConfig(UsuarioService usuarioService) {
//        this.usuarioService = usuarioService;
//    }
    
    @Autowired
    private CustomSuccessHandler customSuccessHandler;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf ->csrf.disable())
                .securityContext(context -> context.requireExplicitSave(false))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/pokemon/**", "/api/auth/**", "/usuario/add", "/usuario", "/login/**", "/api/usuario/**", "/usuario/mostrarMensaje").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
//                    .defaultSuccessUrl("/pokemon", true)
                    .successHandler(customSuccessHandler) //Rutas personalizadas
                    .failureUrl("/login?error=true")
                    .permitAll()
                )
                .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/pokemon?logout=true")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                );
        
        return http.build();
                        
    }
    
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http, UsuarioService usuarioService, PasswordEncoder passwordEncoder)
            throws Exception  {
        
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .userDetailsService(usuarioService)
                .passwordEncoder(passwordEncoder)
                .and()
                .build();
        
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
}
