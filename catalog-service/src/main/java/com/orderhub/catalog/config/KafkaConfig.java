package com.orderhub.catalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Consumers deserialize JSON into the type declared by each {@code @KafkaListener} method
 * parameter (OrderCreated, PaymentFailed and OrderCancelled on different topics), so we rely
 * on the listener's target type instead of a type header — the same approach every other Saga
 * participant uses.
 *
 * <p>catalog-service only consumes; it has no outbox and never publishes a domain event of its
 * own (see the roadmap note on {@code stock.rejected} in ARCHITECTURE.md). The producer
 * template and dead-letter error handler still come from {@code common-kafka}: the
 * dead-letter recoverer needs a producer to park failed records on {@code <topic>.dlt}.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public StringJsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new StringJsonMessageConverter(objectMapper);
    }
}
