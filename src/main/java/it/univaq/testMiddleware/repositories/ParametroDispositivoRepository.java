package it.univaq.testMiddleware.repositories;

import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.ParametroDispositivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParametroDispositivoRepository extends JpaRepository<ParametroDispositivo, Long> {

    // Metodo per cercare un parametro specifico per nome e dispositivo
    Optional<ParametroDispositivo> findByNomeAndDispositivo(String nome, Dispositivo dispositivo);

    // Metodo per trovare tutti i parametri di un dispositivo, usando il nome esatto del campo ID
    List<ParametroDispositivo> findByDispositivo_IdDispositivo(Long dispositivoId);
}