package com.amateur.howmuchfinanceconnector.adapter.out.kafka;

import com.amateur.howmuchfinanceconnector.application.OutboxPublishService;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "finance.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class ConnectorOutboxRelay {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxPublishService outboxService;

    public ConnectorOutboxRelay(KafkaTemplate<String, String> kafkaTemplate, OutboxPublishService outboxService) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${finance.messaging.outbox-delay-ms:1000}")
    public void publish() {
        for (var event : outboxService.pending()) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload()).get(30, TimeUnit.SECONDS);
                outboxService.markPublished(event.getMessageId());
            } catch (Exception exception) {
                return;
            }
        }
    }
}
