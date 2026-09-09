package com.amateur.howmuchfinanceconnector.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectedAccountJpaRepository extends JpaRepository<ConnectedAccountJpaEntity, UUID> {
    List<ConnectedAccountJpaEntity> findAllByConnectionRef(UUID connectionRef);
    Optional<ConnectedAccountJpaEntity> findByAccountRefAndConnectionRefAndActiveTrue(UUID accountRef, UUID connectionRef);
}
