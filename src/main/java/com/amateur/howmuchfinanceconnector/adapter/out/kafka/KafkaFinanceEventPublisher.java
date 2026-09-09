package com.amateur.howmuchfinanceconnector.adapter.out.kafka;

import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceEventOutboxStore;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaFinanceEventPublisher implements FinanceEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaFinanceEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(FinanceEventOutboxStore.PendingFinanceEvent event) {
        try {
            kafkaTemplate.send(event.topic(), event.accountRef().toString(), event.payload()).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Finance event publication was interrupted", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Finance event publication failed", exception.getCause());
        }
    }
}
