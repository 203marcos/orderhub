package com.orderhub.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * Consumers deserialize JSON into the type declared by each {@code @KafkaListener}
 * method parameter (PaymentApproved and PaymentFailed on different topics), so we rely
 * on the listener target type rather than a type header.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public StringJsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new StringJsonMessageConverter(objectMapper);
    }

    /**
     * Retries a failing record three times, then parks it on {@code <topic>.dlt}. This service
     * is the end of the saga: an event dropped here means a customer silently never gets their
     * confirmation e-mail, with nothing left to inspect.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaProperties properties, SslBundles sslBundles) {
        Map<String, Object> producerProperties = properties.buildProducerProperties(sslBundles);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        KafkaTemplate<String, String> deadLetterTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties));

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterTemplate,
                // -1 lets the partitioner choose: the DLT may have fewer partitions than the source.
                (record, exception) -> new TopicPartition(record.topic() + ".dlt", -1));

        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L));
    }
}
