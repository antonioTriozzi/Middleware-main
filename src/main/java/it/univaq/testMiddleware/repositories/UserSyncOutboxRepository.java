package it.univaq.testMiddleware.repositories;

import it.univaq.testMiddleware.models.UserSyncOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSyncOutboxRepository extends JpaRepository<UserSyncOutbox, Long> {
    List<UserSyncOutbox> findTop200ByStatusOrderByIdAsc(String status);
}

