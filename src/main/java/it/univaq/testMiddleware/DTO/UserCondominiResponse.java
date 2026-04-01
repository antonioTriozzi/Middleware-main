package it.univaq.testMiddleware.DTO;

import it.univaq.testMiddleware.DTO.CondominioDTO;
import it.univaq.testMiddleware.DTO.UserDTO;
import lombok.Data;
import java.util.List;

@Data
public class UserCondominiResponse {
    private UserDTO user;
    private List<CondominioDTO> condomini;
}
