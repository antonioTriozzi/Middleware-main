package it.univaq.testMiddleware.repositories;

import it.univaq.testMiddleware.models.Condominio;
import it.univaq.testMiddleware.models.Dispositivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

        List<Dispositivo> findByCondominio(Condominio condominio);

}
