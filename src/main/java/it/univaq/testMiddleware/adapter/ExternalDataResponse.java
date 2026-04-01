package it.univaq.testMiddleware.adapter;

import it.univaq.testMiddleware.DTO.CondominioDTO;
import it.univaq.testMiddleware.DTO.DispositivoDTO;
import it.univaq.testMiddleware.DTO.UserDTO;
import java.util.List;
import java.util.Map;

public class ExternalDataResponse {
    private CondominioDTO condominioDTO;
    private DispositivoDTO dispositivoDTO;
    private UserDTO userDTO;
    private List<Map<String, Object>> parametri;

    // Getters & Setters
    public CondominioDTO getCondominioDTO() {
        return condominioDTO;
    }
    public void setCondominioDTO(CondominioDTO condominioDTO) {
        this.condominioDTO = condominioDTO;
    }
    public DispositivoDTO getDispositivoDTO() {
        return dispositivoDTO;
    }
    public void setDispositivoDTO(DispositivoDTO dispositivoDTO) {
        this.dispositivoDTO = dispositivoDTO;
    }
    public UserDTO getUserDTO() {
        return userDTO;
    }
    public void setUserDTO(UserDTO userDTO) {
        this.userDTO = userDTO;
    }
    public List<Map<String, Object>> getParametri() {
        return parametri;
    }
    public void setParametri(List<Map<String, Object>> parametri) {
        this.parametri = parametri;
    }
}
