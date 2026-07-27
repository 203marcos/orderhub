package com.orderhub.catalog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.orderhub.catalog.dto.ProductResponse;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache wiring is easy to change by accident and impossible to notice in production until
 * something is either stale or unreadable. These pin the decisions that matter.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisConfig")
class RedisConfigTest {

    @Mock RedisConnectionFactory connectionFactory;

    private final RedisConfig config = new RedisConfig();

    private RedisCacheConfiguration productsCacheConfig(Duration ttl) {
        RedisCacheManager manager = config.cacheManager(connectionFactory, ttl);
        // The initial caches are built lazily; without this the configuration map is empty.
        // No Redis connection is opened here — the caches are only described, not used.
        manager.afterPropertiesSet();
        return manager.getCacheConfigurations().get(RedisConfig.PRODUCTS_CACHE);
    }

    @Test
    @DisplayName("applies the configured TTL rather than caching forever")
    void shouldApplyTheConfiguredTtl() {
        assertThat(productsCacheConfig(Duration.ofMinutes(3)).getTtlFunction()
                .getTimeToLive(Object.class, null)).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("does not cache nulls")
    void shouldNotCacheNullValues() {
        // Caching a null would turn one miss into ten minutes of 404s for a product that
        // exists — the classic cache-penetration bug.
        assertThat(productsCacheConfig(Duration.ofMinutes(10)).getAllowCacheNullValues()).isFalse();
    }

    @Test
    @DisplayName("stores values as plain JSON, without polymorphic type information")
    void shouldSerializeWithoutDefaultTyping() {
        ProductResponse product = new ProductResponse(
                UUID.randomUUID(), "Burger", "Cheese burger", new BigDecimal("25.90"),
                "food", true, LocalDateTime.now(), LocalDateTime.now());

        String json = read(productsCacheConfig(Duration.ofMinutes(10))
                .getValueSerializationPair().write(product));

        // No "@class" field: default typing would deserialize whatever type the stored value
        // names, which turns anyone able to write to Redis into someone able to run code here.
        assertThat(json).doesNotContain("@class").contains("\"name\":\"Burger\"");
        // Dates as ISO strings, not epoch numbers, so a cached entry stays readable.
        assertThat(json).doesNotContain("createdAt\":[");
    }

    @Test
    @DisplayName("keeps keys human-readable so entries can be inspected with redis-cli")
    void shouldUseStringKeys() {
        String key = read(productsCacheConfig(Duration.ofMinutes(10))
                .getKeySerializationPair().write("products::42"));

        assertThat(key).isEqualTo("products::42");
    }

    private static String read(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
