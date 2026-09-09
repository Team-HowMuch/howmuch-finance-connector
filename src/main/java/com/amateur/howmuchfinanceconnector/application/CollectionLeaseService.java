package com.amateur.howmuchfinanceconnector.application;

import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionLeaseService {

    private static final Duration LEASE = Duration.ofMinutes(2);
    private final CollectionExecutionJpaRepository repository;

    public CollectionLeaseService(CollectionExecutionJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<CollectionLease> claim() {
        Instant now = Instant.now();
        return repository.findReady(now, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(execution -> new CollectionLease(
                        execution.getSyncJobId(),
                        execution.claim(now, now.plus(LEASE))
                ));
    }
}
