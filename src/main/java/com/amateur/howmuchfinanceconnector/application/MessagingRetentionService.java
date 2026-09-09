package com.amateur.howmuchfinanceconnector.application;

import com.amateur.howmuchfinanceconnector.adapter.out.persistence.OutboxEventJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ProcessedMessageJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessagingRetentionService {

    private final ProcessedMessageJpaRepository processedMessages;
    private final OutboxEventJpaRepository outboxEvents;

    public MessagingRetentionService(
            ProcessedMessageJpaRepository processedMessages,
            OutboxEventJpaRepository outboxEvents
    ) {
        this.processedMessages = processedMessages;
        this.outboxEvents = outboxEvents;
    }

    @Transactional
    @Scheduled(cron = "${finance.messaging.cleanup-cron:0 20 3 * * *}")
    public void deleteExpiredMessages() {
        Instant threshold = Instant.now().minus(72, ChronoUnit.HOURS);
        processedMessages.deleteByCreatedAtBefore(threshold);
        outboxEvents.deleteByStatusAndPublishedAtBefore("PUBLISHED", threshold);
    }
}
