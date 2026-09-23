package com.orderhub.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Consumers deserialize JSON into the type declared by each {@code @KafkaListener}
 * method parameter (this service consumes both PaymentApproved and PaymentFailed on
 * different topics), so we rely on the listener target type rather than a type header.
 *
 * <p>The producer template and the dead-letter error handler are the same in every Saga
 * participant and live in the shared module — see {@code KafkaReliabilityConfig}.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public StringJsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new StringJsonMessageConverter(objectMapper);
    }
}
