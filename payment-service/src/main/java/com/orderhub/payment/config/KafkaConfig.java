package com.orderhub.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Consumers deserialize JSON into the type declared by each {@code @KafkaListener}
 * method parameter. Producers send events without type headers (each service owns its
 * own event classes), so we rely on the listener's target type instead of a header —
 * this works even when one service consumes several event types on different topics.
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
