
package com.digis01.PokeAPICliente.ML;

public class Rol {
    
    public int IdRol;
    public String Nombre;
    
    public Rol() {}
    
    public Rol(com.digis01.PokeAPICliente.JPA.Rol rolJPA) {
        this.IdRol = rolJPA.getIdRol();
        this.Nombre = rolJPA.getNombre();
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
