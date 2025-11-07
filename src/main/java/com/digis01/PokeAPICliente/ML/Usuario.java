
package com.digis01.PokeAPICliente.ML;

import java.util.ArrayList;
import java.util.List;

public class Usuario {
    
    private int IdUsuario;
    private String Nombre;
    private String ApellidoPaterno;
    private String ApellidoMaterno;
    private String Username;
    private String Email;
    private String Password;
    
    public Rol Rol;
    
    public List<Favoritos> Favoritos;
    
    public Usuario() {}
    
    public Usuario (com.digis01.PokeAPICliente.JPA.Usuario usuarioJPA) {
        this.IdUsuario = usuarioJPA.getIdUsuario();
        this.Nombre = usuarioJPA.getNombre();
        this.ApellidoPaterno = usuarioJPA.getApellidoPaterno();
        this.ApellidoMaterno = usuarioJPA.getApellidoMaterno();
        this.Username = usuarioJPA.getUsername();
        this.Email = usuarioJPA.getEmail();
        this.Password = usuarioJPA.getPassword();
        
        this.Rol = new Rol();
        this.Rol.setIdRol(usuarioJPA.Rol.getIdRol());
        this.Rol.setNombre(usuarioJPA.Rol.getNombre());
        
        if (usuarioJPA.Favoritos != null || usuarioJPA.Favoritos.size() > 0)  {
            this.Favoritos = new ArrayList<>();
            
            for (com.digis01.PokeAPICliente.JPA.Favoritos Favorito : usuarioJPA.Favoritos) {
                Favoritos favorito = new Favoritos();
                favorito.setIdFavorito(Favorito.getIdFavorito());
                favorito.setIdPokemon(Favorito.getIdPokemon());
                
                this.Favoritos.add(favorito);
            }
        }
        
    }
    
    public Usuario(int idUsuario, String nombre, String apellidoPaterno, String apellidoMaterno, String username, String email, String password) {
        this.IdUsuario = idUsuario;
        this.Nombre = nombre;
        this.ApellidoPaterno = apellidoMaterno;
        this.ApellidoMaterno = apellidoMaterno;
        this.Username = username;
        this.Email = email;
        this.Password = password;
    }

    public int getIdUsuario() {
        return IdUsuario;
    }

    public void setIdUsuario(int IdUsuario) {
        this.IdUsuario = IdUsuario;
    }

    public String getNombre() {
        return Nombre;
    }

    public void setNombre(String Nombre) {
        this.Nombre = Nombre;
    }

    public String getApellidoPaterno() {
        return ApellidoPaterno;
    }

    public void setApellidoPaterno(String ApellidoPaterno) {
        this.ApellidoPaterno = ApellidoPaterno;
    }

    public String getApellidoMaterno() {
        return ApellidoMaterno;
    }

    public void setApellidoMaterno(String ApellidoMaterno) {
        this.ApellidoMaterno = ApellidoMaterno;
    }

    public String getUsername() {
        return Username;
    }

    public void setUsername(String Username) {
        this.Username = Username;
    }

    public String getEmail() {
        return Email;
    }

    public void setEmail(String Email) {
        this.Email = Email;
    }

    public String getPassword() {
        return Password;
    }

    public void setPassword(String Password) {
        this.Password = Password;
    }

    public Rol getRol() {
        return Rol;
    }

    public void setRol(Rol Rol) {
        this.Rol = Rol;
    }

    public List<Favoritos> getFavoritos() {
        return Favoritos;
    }

    public void setFavoritos(List<Favoritos> Favoritos) {
        this.Favoritos = Favoritos;
    }
    
    
}
