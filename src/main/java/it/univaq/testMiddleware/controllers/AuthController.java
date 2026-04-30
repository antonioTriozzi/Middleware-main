package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.DTO.UserDTO;
import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.repositories.UserRepository;
import it.univaq.testMiddleware.services.JwtService;
import it.univaq.testMiddleware.services.OtpService;
import it.univaq.testMiddleware.services.TokenService;
import it.univaq.testMiddleware.services.UserSyncOutboxService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final DispositivoRepository dispositivoRepository;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final OtpService otpService;
    private final UserSyncOutboxService userSyncOutboxService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository,
                          DispositivoRepository dispositivoRepository,
                          JwtService jwtService,
                          TokenService tokenService,
                          OtpService otpService,
                          UserSyncOutboxService userSyncOutboxService) {
        this.userRepository = userRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.jwtService = jwtService;
        this.tokenService = tokenService;
        this.otpService = otpService;
        this.userSyncOutboxService = userSyncOutboxService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    // Registrazione nuovo utente
    @PostMapping("/register")
    public Map<String, String> register(@RequestParam String username, @RequestParam String password) {
        if (userRepository.existsByUsername(username)) {
            return Map.of("error", "Username già in uso");
        }

        String encryptedPassword = passwordEncoder.encode(password);
        User user = new User();
        user.setUsername(username);
        user.setPassword(encryptedPassword);
        userRepository.save(user);

        return Map.of("message", "Registrazione completata con successo");
    }

    // Login con password criptata e generazione token
    @PostMapping("/login")
    public Map<String, String> login(@RequestParam String username, @RequestParam String password) {
        Optional<User> user = userRepository.findByUsername(username);

        if (user.isPresent() && passwordEncoder.matches(password, user.get().getPassword())) {
            // Genero token
            String token = jwtService.generateToken(username);
            // Salvo token nel DB
            tokenService.saveToken(token, user.get());

            return Map.of("token", token);
        }

        return Map.of("error", "Credenziali errate!");
    }

    /**
     * OTP simulato per CLIENT: genera un codice (valido pochi minuti) e lo stampa nei log.
     * In produzione va sostituito con invio email/SMS.
     */
    @PostMapping("/otp/request")
    public Map<String, String> requestOtp(@RequestParam String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank()) {
            return Map.of("error", "Email mancante");
        }
        // emetti/riusa OTP (throttled)
        String code = otpService.issue(normalized);
        System.out.println("OTP (DEV) per " + normalized + ": " + code);
        return Map.of("message", "OTP generato (controlla i log del backend)");
    }

    /**
     * OTP simulato per CLIENT: verifica il codice e rilascia un JWT.
     */
    @PostMapping("/otp/verify")
    public Map<String, String> verifyOtp(@RequestParam String email, @RequestParam String code) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank() || code == null || code.trim().isBlank()) {
            return Map.of("error", "Email/codice mancanti");
        }

        OtpService.VerifyResult res = otpService.verify(normalized, code.trim());
        if (res != OtpService.VerifyResult.OK) {
            return Map.of("error", switch (res) {
                case EXPIRED -> "Codice scaduto";
                case LOCKED -> "Troppi tentativi, riprova più tardi";
                default -> "Codice non valido";
            });
        }

        // Trova o crea utente CLIENT (source-of-truth: email dal JSON).
        // Importante: se esiste già un utente che fa login come username=partePrimaDi@ (es. "chiara.verdi")
        // ma non ha email valorizzata, lo aggiorniamo invece di creare un duplicato.
        String baseUsername = normalized.contains("@") ? normalized.substring(0, normalized.indexOf('@')) : normalized;
        baseUsername = baseUsername.replaceAll("[^a-zA-Z0-9._-]", "");
        if (baseUsername.isBlank()) baseUsername = "client";

        // Niente lambda: evita vincoli "effectively final" del compilatore.
        User user;
        boolean createdOrPromoted = false;
        Optional<User> byEmail = userRepository.findByEmail(normalized);
        if (byEmail.isPresent()) {
            user = byEmail.get();
        } else {
            Optional<User> byUsername = userRepository.findByUsername(baseUsername);
            if (byUsername.isPresent()) {
                User existing = byUsername.get();
                String existingEmail = existing.getEmail() != null ? existing.getEmail().trim().toLowerCase() : "";
                if (existingEmail.isBlank() || existingEmail.equals(normalized)) {
                    if (existingEmail.isBlank()) {
                        existing.setEmail(normalized);
                        createdOrPromoted = true;
                    }
                    if (existing.getRuolo() == null || existing.getRuolo().isBlank()) {
                        existing.setRuolo("CLIENT");
                        createdOrPromoted = true;
                    }
                    user = userRepository.save(existing);
                } else {
                    // username già occupato con email diversa: crea un nuovo utente con username univoco.
                    User u = new User();
                    u.setEmail(normalized);
                    u.setRuolo("CLIENT");
                    u.setNome("");
                    u.setCognome("");
                    String candidate = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 8);
                    u.setUsername(candidate);
                    u.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    user = userRepository.save(u);
                    createdOrPromoted = true;
                }
            } else {
                User u = new User();
                u.setEmail(normalized);
                u.setRuolo("CLIENT");
                u.setNome("");
                u.setCognome("");
                String candidate = baseUsername;
                if (userRepository.existsByUsername(candidate)) {
                    candidate = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 8);
                }
                u.setUsername(candidate);
                u.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                user = userRepository.save(u);
                createdOrPromoted = true;
            }
        }

        // Salva su outbox nel middleware (il consumer esterno potrà prelevarlo da qui).
        if (createdOrPromoted) {
            try {
                userSyncOutboxService.enqueue(user, "OTP_VERIFY_UPSERT");
            } catch (Exception e) {
                // Non bloccare OTP login per un errore di outbox.
                log.warn("Outbox enqueue failed for email={}: {}", normalized, e.getMessage());
            }
        }

        // Riallineamento: se in passato sono stati creati utenti duplicati, possono esistere dispositivi
        // legati a un altro user_id ma con la stessa owner.email. Spostiamoli sull'utente corrente.
        // Così /api/general/my e /api/consumi/.../mine/... tornano coerenti per l'account OTP.
        for (Dispositivo d : dispositivoRepository.findByOwner_EmailIgnoreCase(normalized)) {
            if (d != null && d.getOwner() != null && !d.getOwner().getId().equals(user.getId())) {
                d.setOwner(user);
                dispositivoRepository.save(d);
            }
        }

        String token = jwtService.generateToken(user.getUsername());
        tokenService.saveToken(token, user);
        return Map.of("token", token);
    }

    // Verifica se il token è valido (nel DB e non scaduto)
    @GetMapping("/validate")
    public Map<String, Boolean> validateToken(@RequestParam String token) {
        boolean isValid = tokenService.findValidToken(token).isPresent();
        return Map.of("valid", isValid);
    }

    // Logout: elimina (o revoca) il token nel DB
    @PostMapping("/logout")
    public Map<String, String> logout(@RequestParam String token) {
        // Se vuoi eliminarlo fisicamente dal DB:
        // tokenService.deleteToken(token);

        // Se invece preferisci revocarlo:
        tokenService.revokeToken(token);

        return Map.of("message", "Logout eseguito, token invalidato con successo!");
    }

    // Nuovo endpoint per ottenere i dati del profilo utente
    @GetMapping("/profile")
    @Transactional
    public ResponseEntity<UserDTO> getProfile(@RequestHeader("Authorization") String authHeader) {
        // Rimuovo il prefisso "Bearer " dal token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = authHeader.substring(7);

        // Verifica se il token è valido
        if (tokenService.findValidToken(token).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Estrai il nome utente dal token
        String username = jwtService.extractUsername(token);
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        User user = userOpt.get();
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNome(user.getNome());
        dto.setCognome(user.getCognome());
        dto.setEmail(user.getEmail());
        dto.setRuolo(user.getRuolo());
        dto.setNumeroCondominiGestiti(user.getCondominiGestiti() != null ? user.getCondominiGestiti().size() : 0);
        System.out.println(dto);
        return ResponseEntity.ok(dto);
    }
}
