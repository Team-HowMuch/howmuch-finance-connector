package com.amateur.howmuchfinanceconnector.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amateur.howmuchfinanceconnector.application.FinanceSyncCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CollectionExecutionJpaEntityTest {

    @Test
    void rejectsResultFromWorkerWhoseLeaseVersionWasReplaced() {
        Instant now = Instant.parse("2026-09-09T08:00:00Z");
        var command = new FinanceSyncCommand(
                UUID.randomUUID(),
                now,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                1,
                null,
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 9),
                now.plusSeconds(600)
        );
        var execution = new CollectionExecutionJpaEntity(command, "hash", now);
        long staleVersion = execution.claim(now, now.plusSeconds(1));
        execution.claim(now.plusSeconds(2), now.plusSeconds(122));

        assertThrows(IllegalStateException.class,
                () -> execution.collected(staleVersion, now.plusSeconds(3)));
    }
}
