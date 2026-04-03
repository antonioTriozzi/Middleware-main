package it.univaq.testMiddleware.repositories;

import it.univaq.testMiddleware.models.Condominio;
import it.univaq.testMiddleware.models.Dispositivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

        List<Dispositivo> findByCondominio(Condominio condominio);

        Optional<Dispositivo> findByIdDispositivoAndCondominio_IdCondominio(Long idDispositivo, Long idCondominio);
}
