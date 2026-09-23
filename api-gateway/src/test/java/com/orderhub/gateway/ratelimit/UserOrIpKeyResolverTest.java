package com.orderhub.gateway.ratelimit;

import com.orderhub.gateway.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserOrIpKeyResolverTest {

    private final UserOrIpKeyResolver resolver = new UserOrIpKeyResolver();

    @Test
    void shouldResolveByUserIdWhenIdentityHeaderIsPresent() {
        String userId = UUID.randomUUID().toString();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/my")
                        .header(JwtAuthenticationFilter.USER_ID_HEADER, userId)
                        .build());

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo(userId);
    }

    @Test
    void shouldFallBackToClientIpWhenNoIdentityHeaderIsPresent() {
        // e.g. a public route such as /auth/login, where JwtAuthenticationFilter never
        // sets X-User-Id — the resolver must key by IP so login attempts are still limited.
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .remoteAddress(new InetSocketAddress("203.0.113.7", 54321))
                        .build());

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("203.0.113.7");
    }

    @Test
    void shouldResolveToUnknownWhenNeitherIdentityNorRemoteAddressIsAvailable() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").build());

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("unknown");
    }
}
