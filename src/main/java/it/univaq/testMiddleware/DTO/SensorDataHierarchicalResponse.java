package it.univaq.testMiddleware.DTO;

import lombok.Data;

// DTO di risposta finale
@Data
public class SensorDataHierarchicalResponse {
    private CondominioDTO condominio;
    private DispositivoDTO dispositivo;
}