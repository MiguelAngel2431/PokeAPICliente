
package com.digis01.PokeAPICliente.DAO;

import com.digis01.PokeAPICliente.JPA.Favoritos;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IRepositoryFavorito extends JpaRepository<Favoritos, Integer> {
    
}
