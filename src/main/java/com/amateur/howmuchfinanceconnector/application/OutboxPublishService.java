package com.amateur.howmuchfinanceconnector.application;

import com.amateur.howmuchfinanceconnector.adapter.out.persistence.OutboxEventJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublishService {

    private final OutboxEventJpaRepository repository;

    public OutboxPublishService(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<OutboxEventJpaEntity> pending() {
        return repository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                "PENDING", Instant.now(), PageRequest.of(0, 20)
        );
    }

    @Transactional
    public void markPublished(UUID messageId) {
        repository.findById(messageId).ifPresent(event -> event.published(Instant.now()));
    }
}
