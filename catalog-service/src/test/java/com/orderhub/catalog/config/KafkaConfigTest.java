package com.orderhub.catalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

    private final KafkaConfig config = new KafkaConfig();

    @Test
    @DisplayName("wires the application's own ObjectMapper into the converter, not a default one")
    void shouldUseTheInjectedObjectMapperNotADefaultOne() {
        // The app's ObjectMapper carries modules (e.g. for LocalDateTime) that a plain
        // "new ObjectMapper()" would not have. Building the converter with the no-arg
        // constructor was a real bug in an earlier revision of this file elsewhere.
        ObjectMapper appObjectMapper = new ObjectMapper();

        StringJsonMessageConverter converter = config.jsonMessageConverter(appObjectMapper);

        Object wiredMapper = ReflectionTestUtils.invokeMethod(converter, "getObjectMapper");
        assertThat(wiredMapper).isSameAs(appObjectMapper);
    }
}
