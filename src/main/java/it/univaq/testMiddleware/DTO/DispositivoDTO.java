package it.univaq.testMiddleware.DTO;

import lombok.Data;

import java.util.List;

// DTO per il dispositivo
@Data
public class DispositivoDTO {
    private Long idDispositivo;
    private Long assetId;
    private String assetName;
    private String externalDeviceId;
    private String nome;
    private String marca;
    private String modello;
    private String tipo;
    private String stato;
    private Long ownerUserId;
    private String ownerMail;
    private List<ParametroDTO> parametri;  // I parametri e i relativi valori
}