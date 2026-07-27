package com.orderhub.common.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * The two settings every Saga participant needs to publish and consume events safely.
 * Each service used to declare both itself, identically.
 */
@Configuration
public class KafkaReliabilityConfig {

    private static final long RETRY_INTERVAL_MS = 1_000L;
    private static final long RETRY_ATTEMPTS = 3L;

    /**
     * Everything these services put on Kafka is already a JSON string: outbox rows hold
     * serialized payloads, and a dead-lettered record carries the source record's raw value.
     * A JSON serializer here would wrap both in a second layer of quoting.
     *
     * <p>Declaring this template makes Spring Boot's auto-configured one back off, which is the
     * intent — no code path sends an unserialized object.
     */
    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<String, String> kafkaTemplate(KafkaProperties properties, SslBundles sslBundles) {
        Map<String, Object> producerProperties = properties.buildProducerProperties(sslBundles);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties));
    }

    /**
     * Retries a failing record, then parks it on {@code <topic>.dlt} instead of dropping it.
     * Spring Boot's default handler logs and moves on, which in this Saga means an order left
     * in PENDING forever with nothing to inspect.
     */
    @Bean
    @ConditionalOnMissingBean(DefaultErrorHandler.class)
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // -1 lets the partitioner choose: the DLT may have fewer partitions than the source.
                (record, exception) -> new TopicPartition(record.topic() + ".dlt", -1));

        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS));
    }
}
