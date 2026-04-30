package it.univaq.testMiddleware.services;

import it.univaq.testMiddleware.models.Token;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.TokenRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.Objects;

@Service
public class TokenService {

    private final TokenRepository tokenRepository;

    public TokenService(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    // Salva un nuovo token nel DB
    public void saveToken(String token, User user) {
        Token newToken = new Token(null, token,
                Instant.now(),
                Instant.now().plusSeconds(86400),
                false,
                user);
        tokenRepository.save(newToken);
    }

    // Cerca un token non revocato
    public Optional<Token> findValidToken(String token) {
        return tokenRepository.findByTokenAndRevokedFalse(token);
    }

    // Revoca il token (setta revoked a true)
    public void revokeToken(String token) {
        tokenRepository.findByToken(token).ifPresent(t -> {
            t.setRevoked(true);
            tokenRepository.save(t);
        });
    }

    // Elimina fisicamente il token dal DB
    public void deleteToken(String token) {
        Optional<Token> opt = tokenRepository.findByToken(token);
        if (opt.isPresent()) {
            Token t = Objects.requireNonNull(opt.get(), "token");
            System.out.println("Elimino il token dal DB: " + t);
            tokenRepository.delete(t);
        } else {
            System.out.println("Nessun token corrispondente trovato in DB per: " + token);
        }
    }
}
