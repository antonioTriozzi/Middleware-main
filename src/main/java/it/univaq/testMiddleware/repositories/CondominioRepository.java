package it.univaq.testMiddleware.repositories;

import it.univaq.testMiddleware.models.Condominio;
import it.univaq.testMiddleware.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CondominioRepository extends JpaRepository<Condominio, Long> {

    List<Condominio> findByAmministratore(User amministratore);
}