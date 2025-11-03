
package com.digis01.PokeAPICliente.JPA;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Rol {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idrol")
    private int IdRol;
    
    @Column(name = "nombre")
    private String Nombre;
    
    public Rol() {}
    
    public Rol(com.digis01.PokeAPICliente.ML.Rol rolML) {
        this.IdRol = rolML.getIdRol();
        this.Nombre = rolML.getNombre();
    }
    
    public Rol(int idRol, String nombre) {
        this.IdRol = idRol;
        this.Nombre = nombre;
    }

    public int getIdRol() {
        return IdRol;
    }

    public void setIdRol(int IdRol) {
        this.IdRol = IdRol;
    }

    public String getNombre() {
        return Nombre;
    }

    public void setNombre(String Nombre) {
        this.Nombre = Nombre;
    }
    
    
    
    
}
