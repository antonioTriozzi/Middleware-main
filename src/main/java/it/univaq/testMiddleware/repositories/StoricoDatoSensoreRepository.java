package it.univaq.testMiddleware.repositories;

import it.univaq.testMiddleware.models.StoricoDatoSensore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoricoDatoSensoreRepository extends JpaRepository<StoricoDatoSensore, Long> {
}