package it.univaq.testMiddleware.services;

import it.univaq.testMiddleware.DTO.UserDTO;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserDataService {

    private static final Logger log = LoggerFactory.getLogger(UserDataService.class);

    /**
     * Esito salvataggio: utente persistito e se è stato accodata la sync verso web (solo utenti nuovi per email).
     */
    public record UserSaveResult(User user, boolean willSyncToWeb) {}

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserSyncOutboxService userSyncOutboxService;

    public UserDataService(UserRepository userRepository,
                           UserSyncOutboxService userSyncOutboxService) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.userSyncOutboxService = userSyncOutboxService;
    }

    /**
     * Upsert per email (evita doppioni). Scrive sempre su tabella {@code user} del middleware.
     * Accoda {@link UserSyncOutboxService#EVENT_WEB_CLIENT_UPSERT} solo se l'utente è appena stato creato
     * (non esisteva già per quella email), così la web riceve il push una volta per nuovo client Android/userdata.
     */
    public User save(UserDTO userDTO) {
        return saveWithResult(userDTO).user();
    }

    public UserSaveResult saveWithResult(UserDTO userDTO) {
        if (userDTO == null || !StringUtils.hasText(userDTO.getEmail())) {
            throw new IllegalArgumentException("Email obbligatoria");
        }
        String normalizedEmail = userDTO.getEmail().trim().toLowerCase();

        Optional<User> existingOpt = userRepository.findByEmail(normalizedEmail);
        boolean isNew = existingOpt.isEmpty();
        User user;

        if (existingOpt.isPresent()) {
            user = existingOpt.get();
            if (StringUtils.hasText(userDTO.getNome())) {
                user.setNome(userDTO.getNome().trim());
            }
            if (StringUtils.hasText(userDTO.getCognome())) {
                user.setCognome(userDTO.getCognome().trim());
            }
            if (StringUtils.hasText(userDTO.getRuolo())) {
                user.setRuolo(userDTO.getRuolo().trim());
            }
            if (userDTO.getIdCondominio() != null) {
                user.setIdCondominio(userDTO.getIdCondominio());
            }
            if (StringUtils.hasText(userDTO.getUsername())) {
                user.setUsername(userDTO.getUsername().trim());
            }
            if (StringUtils.hasText(userDTO.getPassword())) {
                user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
            }
        } else {
            user = new User();
            user.setEmail(normalizedEmail);
            user.setNome(StringUtils.hasText(userDTO.getNome()) ? userDTO.getNome().trim() : "");
            user.setCognome(StringUtils.hasText(userDTO.getCognome()) ? userDTO.getCognome().trim() : "");
            user.setRuolo(StringUtils.hasText(userDTO.getRuolo()) ? userDTO.getRuolo().trim() : "CLIENT");
            user.setIdCondominio(userDTO.getIdCondominio());

            String uname = StringUtils.hasText(userDTO.getUsername()) ? userDTO.getUsername().trim() : deriveUsername(normalizedEmail);
            if (userRepository.existsByUsername(uname)) {
                uname = uname + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            user.setUsername(uname);
            String rawPw = StringUtils.hasText(userDTO.getPassword()) ? userDTO.getPassword() : UUID.randomUUID().toString();
            user.setPassword(passwordEncoder.encode(rawPw));
            try {
                user = userRepository.save(user);
            } catch (DataIntegrityViolationException e) {
                user = userRepository.findByEmail(normalizedEmail).orElseThrow(() -> e);
                isNew = false;
            }
        }

        user = userRepository.save(Objects.requireNonNull(user, "user"));
        log.debug("User upsert committed: id={}, email={}, isNew={}", user.getId(), normalizedEmail, isNew);

        if (isNew) {
            try {
                userSyncOutboxService.enqueue(Objects.requireNonNull(user, "user"), UserSyncOutboxService.EVENT_WEB_CLIENT_UPSERT);
            } catch (Exception e) {
                log.warn("Outbox WEB_CLIENT_UPSERT non accodato per {}: {}", normalizedEmail, e.getMessage(), e);
            }
        }
        return new UserSaveResult(user, isNew);
    }

    private static String deriveUsername(String normalizedEmail) {
        String base = normalizedEmail.contains("@") ? normalizedEmail.substring(0, normalizedEmail.indexOf('@')) : normalizedEmail;
        base = base.replaceAll("[^a-zA-Z0-9._-]", "");
        return base.isBlank() ? "client" : base;
    }
}
