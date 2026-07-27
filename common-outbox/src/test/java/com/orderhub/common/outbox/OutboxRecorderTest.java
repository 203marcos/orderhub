package com.orderhub.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxRecorderTest {

    @Mock OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxRecorder recorder() {
        return new OutboxRecorder(outboxRepository, objectMapper);
    }

    /** Stands in for a service's own event record. */
    record ThingHappened(UUID id, String detail) implements DomainEvent {
        @Override public String aggregateType() { return "Thing"; }
        @Override public UUID aggregateId() { return id; }
        @Override public String eventType() { return "ThingHappened"; }
        @Override public String topic() { return "thing.happened"; }
    }

    private OutboxEvent staged() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldStageTheEventWithTheRoutingItDeclares() {
        UUID id = UUID.randomUUID();

        recorder().record(new ThingHappened(id, "something"));

        OutboxEvent row = staged();
        assertThat(row.getAggregateType()).isEqualTo("Thing");
        assertThat(row.getAggregateId()).isEqualTo(id);
        assertThat(row.getEventType()).isEqualTo("ThingHappened");
        assertThat(row.getTopic()).isEqualTo("thing.happened");
    }

    @Test
    void shouldStageTheEventUnpublishedSoTheRelayPicksItUp() {
        recorder().record(new ThingHappened(UUID.randomUUID(), "something"));

        assertThat(staged().getPublishedAt()).isNull();
    }

    @Test
    void shouldKeyTheMessageByAggregate() {
        UUID id = UUID.randomUUID();

        recorder().record(new ThingHappened(id, "something"));

        assertThat(staged().messageKey()).isEqualTo(id.toString());
    }

    @Test
    void shouldStoreThePayloadSerialized() {
        recorder().record(new ThingHappened(UUID.randomUUID(), "something"));

        assertThat(staged().getPayload()).contains("\"detail\":\"something\"");
    }

    @Test
    void shouldNotLeakRoutingMetadataIntoThePayload() {
        recorder().record(new ThingHappened(UUID.randomUUID(), "something"));

        // Routing is how the event travels, not part of the contract consumers deserialize.
        assertThat(staged().getPayload())
                .doesNotContain("aggregateType", "eventType", "topic", "aggregateId");
    }

    @Test
    void shouldFailTheTransactionWhenAnEventCannotBeSerialized() {
        record Unserializable(UUID id) implements DomainEvent {
            @Override public String aggregateType() { return "Thing"; }
            @Override public UUID aggregateId() { return id; }
            @Override public String eventType() { return "Unserializable"; }
            @Override public String topic() { return "thing.happened"; }
            public Object getBoom() { throw new IllegalStateException("cannot serialize"); }
        }

        // Better to roll the whole change back than to commit one whose event never travels.
        assertThatThrownBy(() -> recorder().record(new Unserializable(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);

        verify(outboxRepository, never()).save(any());
    }
}
