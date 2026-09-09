package com.amateur.howmuchfinanceconnector.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageJpaRepository extends JpaRepository<ProcessedMessageJpaEntity, Long> {
    Optional<ProcessedMessageJpaEntity> findByConsumerNameAndMessageId(String consumerName, UUID messageId);
}
