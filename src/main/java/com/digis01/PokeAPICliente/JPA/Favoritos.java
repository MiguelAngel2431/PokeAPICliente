
package com.digis01.PokeAPICliente.JPA;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import io.micrometer.common.lang.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Entity
@JsonIdentityInfo(
    generator = ObjectIdGenerators.PropertyGenerator.class,
    property = "idFavorito"
)
public class Favoritos {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idfavorito")
    private int IdFavorito;
    
    @Column(name = "idpokemon")
    private int IdPokemon;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "idusuario", nullable = false)
    @JsonProperty("usuario")
//    @JsonBackReference
    private Usuario Usuario;
    
    public Favoritos () {}
    
    public Favoritos (com.digis01.PokeAPICliente.ML.Favoritos favoritoML) {
        this.IdFavorito = favoritoML.getIdFavorito();
        this.IdPokemon = favoritoML.getIdPokemon();
        
        this.Usuario = new Usuario();
        this.Usuario.setIdUsuario(favoritoML.getIdUsuario());
    }

    public int getIdFavorito() {
        return IdFavorito;
    }

    public void setIdFavorito(int IdFavorito) {
        this.IdFavorito = IdFavorito;
    }

    public int getIdPokemon() {
        return IdPokemon;
    }

    public void setIdPokemon(int IdPokemon) {
        this.IdPokemon = IdPokemon;
    }

    public Usuario getUsuario() {
        return Usuario;
    }

    public void setUsuario(Usuario Usuario) {
        this.Usuario = Usuario;
    }
    
    
    
}
