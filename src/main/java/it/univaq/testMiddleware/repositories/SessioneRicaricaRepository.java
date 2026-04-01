package it.univaq.testMiddleware.repositories;

import it.univaq.testMiddleware.models.SessioneRicarica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SessioneRicaricaRepository extends JpaRepository<SessioneRicarica, Long> {
    List<SessioneRicarica> findByColonnina_IdDispositivo(Long colonninaId);
}
