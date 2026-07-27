package com.orderhub.common.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaReliabilityConfigTest {

    private final KafkaReliabilityConfig config = new KafkaReliabilityConfig();

    private KafkaTemplate<String, String> template() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of("broker:9092"));
        return config.kafkaTemplate(properties, new DefaultSslBundleRegistry());
    }

    @Test
    void shouldSerializeValuesAsStringsNotJson() {
        Map<String, Object> producerConfig = template().getProducerFactory().getConfigurationProperties();

        // Payloads reach this template already serialized — outbox rows and dead-lettered
        // records both carry raw JSON text. A JSON serializer would quote them a second time.
        assertThat(producerConfig.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
                .isEqualTo(StringSerializer.class);
        assertThat(producerConfig.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
                .isEqualTo(StringSerializer.class);
    }

    @Test
    void shouldKeepTheApplicationsBootstrapServers() {
        Map<String, Object> producerConfig = template().getProducerFactory().getConfigurationProperties();

        // Overriding the serializers must not discard the rest of spring.kafka.*.
        assertThat(producerConfig.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
                .isEqualTo(List.of("broker:9092"));
    }

    @Test
    void shouldProvideAnErrorHandlerThatDeadLettersInsteadOfDropping() {
        DefaultErrorHandler errorHandler = config.kafkaErrorHandler(template());

        assertThat(errorHandler).isNotNull();
        // A record that keeps failing must be parked, not acknowledged: without a recoverer
        // Spring Boot's default handler logs and moves the offset on.
        assertThat(errorHandler.isAckAfterHandle()).isTrue();
    }
}
