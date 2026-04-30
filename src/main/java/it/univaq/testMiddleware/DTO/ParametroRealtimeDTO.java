package it.univaq.testMiddleware.DTO;

import lombok.Data;

import java.time.Instant;

/**
 * DTO compatibile con la schermata mobile "Realtime".
 */
@Data
public class ParametroRealtimeDTO {
    private String nome;
    private String tipologia;
    private String unitaMisura;
    private Double valMin;
    private Double valMax;
    private Double maxDelta;
    private String valore;
    private Instant timestamp;
}

