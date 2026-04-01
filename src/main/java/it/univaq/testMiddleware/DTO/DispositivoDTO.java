package it.univaq.testMiddleware.DTO;

import lombok.Data;

import java.util.List;

// DTO per il dispositivo
@Data
public class DispositivoDTO {
    private Long idDispositivo;
    private String nome;
    private String marca;
    private String modello;
    private String tipo;
    private String stato;
    private List<ParametroDTO> parametri;  // I parametri e i relativi valori
}