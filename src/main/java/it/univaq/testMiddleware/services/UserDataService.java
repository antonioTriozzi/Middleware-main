package it.univaq.testMiddleware.services;
import it.univaq.testMiddleware.DTO.UserDTO;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserDataService {
  

    private final UserRepository UserRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserDataService(UserRepository userRepository)
    {
        this.UserRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public User save(UserDTO userDTO) {
        User user = new User();
        user.setUsername(userDTO.getUsername());
        String hashedPassword = passwordEncoder.encode(userDTO.getPassword());
        user.setPassword(hashedPassword);
        user.setEmail(userDTO.getEmail());
        user.setCognome(userDTO.getCognome());
        user.setNome(userDTO.getNome());
        user.setRuolo(userDTO.getRuolo());
        return UserRepository.save(user);
    }
    
}