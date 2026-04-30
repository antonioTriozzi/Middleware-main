package it.univaq.testMiddleware.config;

import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea un solo utente ADMIN nel DB se non esiste.
 * Serve per garantire sempre una credenziale amministrativa.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;

    @Value("${app.bootstrap.admin.username:admin}")
    private String adminUsername;

    @Value("${app.bootstrap.admin.password:AdminPass123!}")
    private String adminPassword;

    @Value("${app.bootstrap.admin.email:admin@example.com}")
    private String adminEmail;

    public AdminBootstrap(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }

        User u = new User();
        u.setUsername(adminUsername);
        u.setEmail(adminEmail);
        u.setRuolo("ADMIN");
        u.setNome("Admin");
        u.setCognome("");
        u.setPassword(new BCryptPasswordEncoder().encode(adminPassword));

        userRepository.save(u);
    }
}

