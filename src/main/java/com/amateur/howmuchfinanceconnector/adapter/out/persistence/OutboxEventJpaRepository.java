package com.amateur.howmuchfinanceconnector.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
    List<OutboxEventJpaEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            String status,
            Instant now,
            Pageable pageable
    );
    long deleteByStatusAndPublishedAtBefore(String status, Instant threshold);
}
