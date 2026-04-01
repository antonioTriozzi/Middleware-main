package it.univaq.testMiddleware.repositories;

import it.univaq.testMiddleware.models.StoricoEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoricoEventoRepository extends JpaRepository<StoricoEvento, Long> {
    List<StoricoEvento> findByDispositivo_Condominio_Amministratore_Id(Long userId);
}

