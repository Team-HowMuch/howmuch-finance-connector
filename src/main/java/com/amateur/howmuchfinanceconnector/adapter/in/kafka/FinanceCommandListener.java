package com.amateur.howmuchfinanceconnector.adapter.in.kafka;

import com.amateur.howmuchfinanceconnector.application.AcceptFinanceCommandService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "finance.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class FinanceCommandListener {

    private final AcceptFinanceCommandService service;

    public FinanceCommandListener(AcceptFinanceCommandService service) {
        this.service = service;
    }

    @KafkaListener(
            topics = "${finance.kafka.command-topic:finance.sync.command.v1}",
            groupId = "${finance.kafka.connector-group-id:finance-connector-sync-v1}"
    )
    public void onCommand(String payload) {
        service.accept(payload);
    }
}
