package it.univaq.testMiddleware.DTO;

import lombok.Data;

import java.util.List;

// DTO per il parametro
@Data
public class ParametroDTO {
    private String nome;
    private String tipologia;
    private String unitaMisura;
    private List<SensorValueDTO> valori;
}