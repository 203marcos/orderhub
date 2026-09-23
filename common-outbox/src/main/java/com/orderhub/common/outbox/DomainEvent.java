package com.orderhub.common.outbox;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

/**
 * An event a service publishes as part of the Saga.
 *
 * <p>The event carries its own routing. That keeps the topic name next to the payload it
 * describes — where you look for it — instead of scattering topic constants through the
 * services, and it means {@link OutboxRecorder} needs no {@code switch} over event types:
 * a new event is a new record implementing this interface, with nothing else to change.
 *
 * <p>This interface is the only thing the shared module asks of a service's domain. It never
 * declares the event types themselves: each service owns its own copy of the records it
 * produces and consumes, so this library cannot become a back door for one service to depend
 * on another's model.
 */
public interface DomainEvent {

    /** The kind of thing the event happened to, e.g. {@code "Order"}. Recorded for tracing. */
    @JsonIgnore
    String aggregateType();

    /** Doubles as the Kafka message key, so events about one aggregate keep their order. */
    @JsonIgnore
    UUID aggregateId();

    /** The event's own name, e.g. {@code "OrderCreated"}. */
    @JsonIgnore
    String eventType();

    /** The Kafka topic this event belongs on. */
    @JsonIgnore
    String topic();
}
