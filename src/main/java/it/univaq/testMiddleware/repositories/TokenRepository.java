package it.univaq.testMiddleware.repositories;

import it.univaq.testMiddleware.models.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    // Trova un token valido (non revocato)
    Optional<Token> findByTokenAndRevokedFalse(String token);

    // Trova un token specifico
    Optional<Token> findByToken(String token);

    
}
