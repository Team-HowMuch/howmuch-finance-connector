package com.amateur.howmuchfinanceconnector.adapter.out.persistence;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionExecutionJpaRepository extends JpaRepository<CollectionExecutionJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select execution from CollectionExecutionJpaEntity execution
            where (execution.status in ('ACCEPTED', 'RETRY') and execution.nextAttemptAt <= :now)
               or (execution.status = 'COLLECTING' and execution.leaseUntil < :now)
            order by execution.createdAt asc
            """)
    List<CollectionExecutionJpaEntity> findReady(@Param("now") Instant now, Pageable pageable);
}
