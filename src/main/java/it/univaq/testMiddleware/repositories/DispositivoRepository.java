package it.univaq.testMiddleware.repositories;

import it.univaq.testMiddleware.models.Condominio;
import it.univaq.testMiddleware.models.Dispositivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

        List<Dispositivo> findByCondominio(Condominio condominio);

        Optional<Dispositivo> findByIdDispositivoAndCondominio_IdCondominio(Long idDispositivo, Long idCondominio);

        Optional<Dispositivo> findByAssetIdAndCondominio_IdCondominio(Long assetId, Long idCondominio);

        Optional<Dispositivo> findByExternalDeviceIdAndCondominio_IdCondominio(String externalDeviceId, Long idCondominio);

        List<Dispositivo> findByCondominio_IdCondominioAndOwner_Id(Long idCondominio, Long ownerId);

        List<Dispositivo> findByOwner_Id(Long ownerId);

        // Utility: riallineamento owner dopo OTP/login (email come chiave)
        List<Dispositivo> findByOwner_EmailIgnoreCase(String email);

        @Query("select distinct d.condominio from Dispositivo d where d.owner.id = :ownerId and d.condominio is not null")
        List<Condominio> findDistinctCondominiByOwnerId(@Param("ownerId") Long ownerId);
}
