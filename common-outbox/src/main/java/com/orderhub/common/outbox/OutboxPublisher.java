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
 * Relays committed outbox rows to Kafka — the "publish" half of the transactional outbox.
 *
 * <p>The business transaction never talks to Kafka; it only appends a row (see
 * {@link OutboxRecorder}). This runs afterwards, on a timer, and does the actual sending:
 *
 * <pre>
 * every 500 ms, in one transaction:
 *   claim up to N unpublished rows, oldest first, FOR UPDATE SKIP LOCKED
 *   for each: send to Kafka, wait for the broker's ack, stamp published_at
 * </pre>
 *
 * <p>Three decisions in that loop are worth understanding, because each one is a bug if
 * reversed:
 *
 * <ul>
 *   <li><b>Claim with {@code SKIP LOCKED}</b> — rows a sibling replica is already working on
 *       are stepped over rather than waited for. Without it, two instances either serialise
 *       behind each other or publish the same event twice.</li>
 *   <li><b>Send, then mark</b> — never the reverse. Marking first would lose the event outright
 *       if the broker were down. Marking second means a crash between the two re-sends it,
 *       which is why delivery here is <b>at-least-once, not exactly-once</b>. That is a
 *       deliberate trade: duplicates are survivable, silent loss is not. The consumers carry
 *       the matching duplicate guards.</li>
 *   <li><b>Stop at the first failure</b> — the batch is ordered oldest-first, so skipping a
 *       failed row and publishing the next one could deliver a later event for the same
 *       aggregate before an earlier one.</li>
 * </ul>
 *
 * <p>The whole batch shares one transaction, so the claimed rows stay locked until it commits.
 * That bounds how long a stuck broker can hold them: keep {@code outbox.batch-size} modest.
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
