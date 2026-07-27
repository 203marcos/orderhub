package com.orderhub.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * The write side of the outbox: stages a domain event for publication.
 *
 * <p>Callers hand over an event and nothing else. Serialization, the outbox row and the topic
 * are this class's business, so a service's business logic never mentions Jackson or Kafka —
 * it just states that something happened.
 *
 * <p>Must be called from inside the transaction that changed the aggregate. That is the whole
 * point: the row and the aggregate commit together, or neither does.
 */
@Component
public class OutboxRecorder {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxRecorder(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void record(DomainEvent event) {
        outboxRepository.save(new OutboxEvent(
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.topic(),
                serialize(event)));
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            // An event we cannot serialize is a programming error. Failing here rolls the
            // business transaction back, which is right: better no change at all than a
            // committed change whose event never reaches the rest of the Saga.
            throw new IllegalStateException(
                    "Could not serialize " + event.eventType() + " for " + event.aggregateId(), ex);
        }
    }
}
