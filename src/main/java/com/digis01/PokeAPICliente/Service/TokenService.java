package com.digis01.PokeAPICliente.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    //Almacenamos los tokens en memoria
    private final ConcurrentHashMap<String, TokenData> tokenStore = new ConcurrentHashMap<>();

    public static class TokenData {

        private String email;
        private LocalDateTime expirationDate;

        public TokenData(String email, LocalDateTime expirationDate) {
            this.email = email;
            this.expirationDate = expirationDate;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public LocalDateTime getExpirationDate() {
            return expirationDate;
        }

        public void setExpirationDate(LocalDateTime expirationDate) {
            this.expirationDate = expirationDate;
        }

    }

    //Método para almacenar el token
    public void storeToken(String token, String email, LocalDateTime expirationDate) {
        tokenStore.put(token, new TokenData(email, expirationDate));
    }
    
    //Metodo para validar token
    public String validateToken(String token) {
        TokenData tokenData = tokenStore.get(token);
        
        if (tokenData == null) {
            return null;
        }
        
        //Verificamos si el token ha expirado
        if (tokenData.getExpirationDate().isBefore(LocalDateTime.now())) {
            tokenStore.remove(token);
            return null;
        }
        
        return tokenData.getEmail(); //Si el token es valido, devolvemos el correo asociado
    }
    
    //Generar token para ingresar al sistema
    public String generateVerificationToken(String email) {
        
        String token = UUID.randomUUID().toString();
        LocalDateTime expiration = LocalDateTime.now().plusMinutes(1); //1 minuto
        storeToken(token, email, expiration);
        return token;
        
    }
}
