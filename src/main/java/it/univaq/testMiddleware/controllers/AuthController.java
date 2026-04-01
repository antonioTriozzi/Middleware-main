package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.DTO.UserDTO;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.UserRepository;
import it.univaq.testMiddleware.services.JwtService;
import it.univaq.testMiddleware.services.TokenService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository,
                          JwtService jwtService,
                          TokenService tokenService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.tokenService = tokenService;
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
        dto.setNumeroCondominiGestiti(user.getCondominiGestiti() != null ? user.getCondominiGestiti().size() : 0);
        System.out.println(dto);
        return ResponseEntity.ok(dto);
    }
}
