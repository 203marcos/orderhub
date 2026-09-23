package com.orderhub.catalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderhub.catalog.dto.ProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Cache-aside caching for the product catalog, backed by Redis.
 *
 * <p>Reads go through {@code @Cacheable("products")}; writes and deletes evict the
 * entry with {@code @CacheEvict}. Values are stored as plain JSON bound to
 * {@link ProductResponse} — no polymorphic {@code @class} type info — which avoids the
 * deserialization-gadget risk of default typing and makes entries easy to inspect
 * with {@code redis-cli GET products::<id>}. TTL is configurable via
 * {@code catalog.cache.products.ttl} (default 10m).
 */
@Configuration
@EnableCaching
public class RedisConfig {

    public static final String PRODUCTS_CACHE = "products";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory,
                                          @Value("${catalog.cache.products.ttl:10m}") Duration ttl) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RedisCacheConfiguration productsConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, ProductResponse.class)));

        return RedisCacheManager.builder(factory)
                .withInitialCacheConfigurations(Map.of(PRODUCTS_CACHE, productsConfig))
                .build();
    }
}
