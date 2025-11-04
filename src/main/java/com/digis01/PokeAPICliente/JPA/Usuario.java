
package com.digis01.PokeAPICliente.JPA;

import io.micrometer.common.lang.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Usuario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idusuario")
    private int IdUsuario;
    
    @Column(name = "nombre")
    private String Nombre;
    
    @Column(name = "apellidopaterno")
    private String ApellidoPaterno;
    
    @Column(name = "apellidomaterno")
    private String ApellidoMaterno;
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "email")
    private String Email;
    
    @Column(name = "password")
    private String Password;
    
    @ManyToOne
    @JoinColumn(name = "idrol", nullable = false)
    @Nullable
    public Rol Rol;
    
    public Usuario () {}
    
    public Usuario (com.digis01.PokeAPICliente.ML.Usuario usuarioML) {
        this.IdUsuario = usuarioML.getIdUsuario();
        this.Nombre = usuarioML.getNombre();
        this.ApellidoPaterno = usuarioML.getApellidoPaterno();
        this.ApellidoMaterno = usuarioML.getApellidoMaterno();
        this.username = usuarioML.getUsername();
        this.Email = usuarioML.getEmail();
        this.Password = usuarioML.getPassword();
        
        this.Rol = new Rol();
        this.Rol.setIdRol(usuarioML.Rol.getIdRol());
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
        return username;
    }

    public void setUsername(String Username) {
        this.username = Username;
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
}
