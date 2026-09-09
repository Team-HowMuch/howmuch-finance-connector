package com.amateur.howmuchfinanceconnector.adapter.in.schedule;

import com.amateur.howmuchfinanceconnector.application.CodefCollectionService;
import com.amateur.howmuchfinanceconnector.application.CollectionLeaseService;
import com.amateur.howmuchfinanceconnector.application.CollectionResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "finance.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class CollectionWorker {

    private static final Logger log = LoggerFactory.getLogger(CollectionWorker.class);
    private final CollectionLeaseService leases;
    private final CodefCollectionService collector;
    private final CollectionResultStore results;

    public CollectionWorker(
            CollectionLeaseService leases,
            CodefCollectionService collector,
            CollectionResultStore results
    ) {
        this.leases = leases;
        this.collector = collector;
        this.results = results;
    }

    @Scheduled(fixedDelayString = "${finance.collection.poll-delay-ms:1000}")
    public void collect() {
        for (int count = 0; count < 20; count++) {
            var lease = leases.claim();
            if (lease.isEmpty()) return;
            try {
                collector.collect(lease.get());
            } catch (RuntimeException exception) {
                log.warn("Finance collection failed syncJobId={}", lease.get().syncJobId(), exception);
                try {
                    results.retryOrFail(lease.get(), "COLLECTION_INTERNAL_ERROR");
                } catch (RuntimeException staleLease) {
                    log.info("Ignored stale finance collection result syncJobId={}", lease.get().syncJobId());
                }
            }
        }
    }

}
