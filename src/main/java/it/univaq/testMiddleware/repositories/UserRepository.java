package it.univaq.testMiddleware.repositories;

import it.univaq.testMiddleware.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Trova un utente tramite username
    Optional<User> findByUsername(String username);

    // Controlla se un utente esiste già
    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
