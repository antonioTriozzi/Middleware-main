package it.univaq.testMiddleware.DTO;

import lombok.Data;

import java.util.List;

@Data
public class UserCondominiResponse {
    private UserDTO user;
    private List<CondominioDTO> condomini;
}
