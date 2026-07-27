package com.orderhub.common.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaReliabilityConfig")
class KafkaReliabilityConfigTest {

    private static final List<String> CONFIGURED = List.of("configured:9092");
    private static final List<String> RESOLVED = List.of("resolved:19092");

    private final KafkaReliabilityConfig config = new KafkaReliabilityConfig();

    /**
     * Deliberately disagrees with {@code spring.kafka.bootstrap-servers}, so a producer built
     * from properties alone is distinguishable from one built from connection details.
     */
    private static KafkaConnectionDetails connectionDetails() {
        return () -> RESOLVED;
    }

    private Map<String, Object> producerConfig() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(CONFIGURED);
        KafkaTemplate<String, String> template =
                config.kafkaTemplate(properties, connectionDetails(), new DefaultSslBundleRegistry());
        return template.getProducerFactory().getConfigurationProperties();
    }

    @Test
    @DisplayName("takes the broker address from connection details, not from the property")
    void shouldPreferConnectionDetailsOverTheConfiguredProperty() {
        // This is what makes @ServiceConnection work. Boot's own producer factory applies the
        // same override; taking the template over means taking this over with it. Building
        // from KafkaProperties alone sends to the configured default instead of the
        // Testcontainers broker, and the integration tests fail with
        // "Bootstrap broker localhost:9092 disconnected".
        assertThat(producerConfig().get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
                .isEqualTo(RESOLVED)
                .isNotEqualTo(CONFIGURED);
    }

    @Test
    @DisplayName("serializes values as strings, not JSON")
    void shouldSerializeValuesAsStringsNotJson() {
        // Payloads reach this template already serialized — outbox rows and dead-lettered
        // records both carry raw JSON text. A JSON serializer would quote them a second time.
        Map<String, Object> producerConfig = producerConfig();
        assertThat(producerConfig.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
                .isEqualTo(StringSerializer.class);
        assertThat(producerConfig.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
                .isEqualTo(StringSerializer.class);
    }

    @Test
    @DisplayName("keeps the rest of spring.kafka.producer.*")
    void shouldKeepTheRemainingProducerProperties() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(CONFIGURED);
        properties.getProducer().setAcks("all");

        Map<String, Object> producerConfig = config
                .kafkaTemplate(properties, connectionDetails(), new DefaultSslBundleRegistry())
                .getProducerFactory().getConfigurationProperties();

        // Overriding the address and the serializers must not discard everything else.
        assertThat(producerConfig.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
    }

    @Test
    @DisplayName("provides an error handler that dead-letters instead of dropping")
    void shouldProvideAnErrorHandlerThatDeadLetters() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(CONFIGURED);
        KafkaTemplate<String, String> template =
                config.kafkaTemplate(properties, connectionDetails(), new DefaultSslBundleRegistry());

        DefaultErrorHandler errorHandler = config.kafkaErrorHandler(template);

        assertThat(errorHandler).isNotNull();
        // A record that keeps failing must be parked, not acknowledged: without a recoverer
        // Spring Boot's default handler logs and moves the offset on.
        assertThat(errorHandler.isAckAfterHandle()).isTrue();
    }
}
