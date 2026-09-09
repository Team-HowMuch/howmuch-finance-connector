package com.amateur.howmuchfinanceconnector.adapter.in.kafka;

import com.amateur.howmuchfinanceconnector.application.port.in.sync.AcceptFinanceSyncCommandUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "finance.messaging.enabled", havingValue = "true")
public class FinanceSyncCommandListener {

    private final FinanceSyncCommandParser parser;
    private final AcceptFinanceSyncCommandUseCase useCase;

    public FinanceSyncCommandListener(
            FinanceSyncCommandParser parser,
            AcceptFinanceSyncCommandUseCase useCase
    ) {
        this.parser = parser;
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${finance.messaging.command-topic}")
    public void accept(ConsumerRecord<String, String> record) {
        useCase.accept(parser.parse(record.key(), record.value()));
    }
}
