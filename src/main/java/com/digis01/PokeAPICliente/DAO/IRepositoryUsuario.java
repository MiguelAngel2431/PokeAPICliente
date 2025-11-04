/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.digis01.PokeAPICliente.DAO;

import com.digis01.PokeAPICliente.JPA.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author Alien 5
 */
public interface IRepositoryUsuario extends JpaRepository<Usuario, Integer> {
    Usuario findByUsername(String username);
    
}
