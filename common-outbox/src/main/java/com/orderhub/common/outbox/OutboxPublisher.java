package com.orderhub.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Relays committed outbox rows to Kafka.
 *
 * <p>Delivery is at-least-once by construction: the send may succeed and the transaction that
 * marks the row published may still fail, in which case the event is sent again on the next
 * tick. Consumers are written to tolerate that — see the duplicate guards in the saga handlers.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${outbox.batch-size:100}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending =
                outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(Limit.of(batchSize));

        for (OutboxEvent event : pending) {
            try {
                // Block on the send: only a broker-acknowledged event may be marked published,
                // otherwise a broker outage would silently drop it.
                kafkaTemplate.send(event.getTopic(), event.messageKey(), event.getPayload()).get();
                event.markPublished();
                log.debug("Relayed {} for {} to {}",
                        event.getEventType(), event.getAggregateId(), event.getTopic());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                // Leave the row unpublished and stop: retrying on the next tick preserves the
                // per-aggregate ordering that continuing past a failure would break.
                log.error("Failed to relay outbox event {}, will retry", event.getId(), ex);
                return;
            }
        }
    }
}
