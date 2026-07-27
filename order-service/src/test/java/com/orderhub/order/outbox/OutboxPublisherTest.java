package com.orderhub.order.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock OutboxRepository outboxRepository;
    @Mock KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher publisher() {
        return new OutboxPublisher(outboxRepository, kafkaTemplate, 100);
    }

    private static OutboxEvent pendingEvent(UUID aggregateId) {
        return new OutboxEvent("Order", aggregateId, "OrderCreated", "order.created", "{\"orderId\":\"x\"}");
    }

    @Test
    void shouldRelayPendingEventsAndMarkThemPublished() {
        OutboxEvent event = pendingEvent(UUID.randomUUID());
        when(outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Limit.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("order.created"), eq(event.messageKey()), eq(event.getPayload())))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher().publishPending();

        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void shouldKeyMessagesByAggregateSoOrderIsPreservedPerOrder() {
        UUID orderId = UUID.randomUUID();
        OutboxEvent event = pendingEvent(orderId);
        when(outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Limit.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher().publishPending();

        verify(kafkaTemplate).send("order.created", orderId.toString(), event.getPayload());
    }

    @Test
    void shouldLeaveEventUnpublishedWhenTheBrokerRejectsIt() {
        OutboxEvent event = pendingEvent(UUID.randomUUID());
        when(outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Limit.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        publisher().publishPending();

        // Still pending, so the next tick retries it rather than losing the event.
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void shouldStopAtTheFirstFailureToPreservePerAggregateOrdering() {
        OutboxEvent first = pendingEvent(UUID.randomUUID());
        OutboxEvent second = pendingEvent(UUID.randomUUID());
        when(outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Limit.class)))
                .thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        publisher().publishPending();

        assertThat(second.getPublishedAt()).isNull();
        verify(kafkaTemplate, never()).send(any(), eq(second.messageKey()), any());
    }

    @Test
    void shouldDoNothingWhenThereIsNoBacklog() {
        when(outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Limit.class)))
                .thenReturn(List.of());

        publisher().publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }
}
