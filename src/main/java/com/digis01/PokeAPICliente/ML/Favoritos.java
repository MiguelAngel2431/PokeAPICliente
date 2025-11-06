
package com.digis01.PokeAPICliente.ML;

public class Favoritos {
    
    private int IdFavorito;
    private int IdPokemon;
    
    public int IdUsuario;
    
    public Favoritos () {}
    
    public Favoritos (int idFavorito, int idPokemon, int idUsuario) {
        this.IdFavorito = idFavorito;
        this.IdPokemon = idPokemon;
        this.IdUsuario = idUsuario;
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

    public int getIdUsuario() {
        return IdUsuario;
    }

    public void setIdUsuario(int IdUsuario) {
        this.IdUsuario = IdUsuario;
    }

    
}
