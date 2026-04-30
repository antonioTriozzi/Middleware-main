package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.DTO.DispositivoDTO;
import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.services.UserService;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Endpoint per CLIENT: restituisce solo i dispositivi di cui l'utente è owner.
 */
@RestController
@RequestMapping("/api")
public class MyDevicesController {

    private final UserService userService;
    private final DispositivoRepository dispositivoRepository;

    public MyDevicesController(UserService userService, DispositivoRepository dispositivoRepository) {
        this.userService = userService;
        this.dispositivoRepository = dispositivoRepository;
    }

    @GetMapping("/condomini/{idCondominio}/dispositivi/mine")
    @Transactional
    public ResponseEntity<?> getMyDevices(@PathVariable Long idCondominio) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username);

        List<Dispositivo> dispositivi = dispositivoRepository.findByCondominio_IdCondominioAndOwner_Id(idCondominio, user.getId());
        List<DispositivoDTO> dto = dispositivi.stream().map(this::mapToDispositivoDTO).collect(Collectors.toList());
        return ResponseEntity.ok(dto);
    }

    private DispositivoDTO mapToDispositivoDTO(Dispositivo dispositivo) {
        DispositivoDTO dto = new DispositivoDTO();
        dto.setIdDispositivo(dispositivo.getIdDispositivo());
        dto.setAssetId(dispositivo.getAssetId());
        dto.setAssetName(dispositivo.getAssetName());
        dto.setExternalDeviceId(dispositivo.getExternalDeviceId());
        dto.setNome(dispositivo.getNome());
        dto.setMarca(dispositivo.getMarca());
        dto.setModello(dispositivo.getModello());
        dto.setTipo(dispositivo.getTipo());
        dto.setStato(dispositivo.getStato());
        if (dispositivo.getOwner() != null) {
            dto.setOwnerUserId(dispositivo.getOwner().getId());
            dto.setOwnerMail(dispositivo.getOwner().getEmail());
        }
        return dto;
    }
}

