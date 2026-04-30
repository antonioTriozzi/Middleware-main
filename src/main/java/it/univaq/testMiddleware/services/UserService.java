package it.univaq.testMiddleware.services;

import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            throw new UsernameNotFoundException("Utente non trovato: " + username);
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.get().getUsername())
                .password(user.get().getPassword()) // La password deve essere criptata!
                .roles("USER")
                .build();
    }

    // Metodo custom per ottenere il dominio User a partire dal username
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato: " + username));
    }

    public User save(User user) {
        Objects.requireNonNull(user, "user");
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            String normalized = user.getEmail().trim().toLowerCase();
            user.setEmail(normalized);
            Optional<User> byMail = userRepository.findByEmail(normalized);
            if (byMail.isPresent()) {
                User existing = byMail.get();
                if (user.getId() == null || !existing.getId().equals(user.getId())) {
                    return existing;
                }
            }
        }
        return userRepository.save(user);
    }
}
