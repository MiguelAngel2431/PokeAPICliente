
package com.digis01.PokeAPICliente.DAO;

import com.digis01.PokeAPICliente.JPA.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IRepositoryUsuario extends JpaRepository<Usuario, Integer> {
    Usuario findByUsername(String username);
    Usuario findByEmail(String email);
}

