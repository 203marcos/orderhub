package com.orderhub.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "dGVzdFNlY3JldEtleUZvckp3dFRlc3RpbmdPcmRlckh1YlByb2plY3QyMDI2IQ==";

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "secret", SECRET);
        chain = mock(GatewayFilterChain.class);
    }

    @Test
    void shouldLetPublicAuthPathThroughWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").build());
        // Matched with any(): the filter always forwards a mutated exchange — inbound identity
        // headers are stripped even here — so it is never the same instance it received.
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void shouldReturn401WhenAuthorizationHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/my").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void shouldReturn401WhenTokenIsInvalid() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void shouldStripAForgedIdentityHeaderOnAPublicPath() {
        // A public route is forwarded untouched, so if the stripping happened after the
        // public-path check a client could set X-User-Id here and have it reach a service.
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN")
                        .build());

        var captor = org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = captor.getValue().getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getFirst("X-User-Role")).isNull();
    }

    @Test
    void shouldOverrideAForgedIdentityHeaderWithTheTokensOwn() {
        UUID realUser = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        String token = validToken(realUser, "user@example.com", "USER");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        // Attacker presents their own valid token but claims to be someone else.
                        .header("X-User-Id", victim.toString())
                        .header("X-User-Role", "ADMIN")
                        .build());

        var captor = org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = captor.getValue().getRequest().getHeaders();
        assertThat(forwarded.get("X-User-Id")).containsExactly(realUser.toString());
        assertThat(forwarded.get("X-User-Role")).containsExactly("USER");
    }

    @Test
    void shouldForwardIdentityHeadersWhenTokenIsValid() {
        UUID userId = UUID.randomUUID();
        String token = validToken(userId, "user@example.com", "USER");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        var captor = org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = captor.getValue().getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isEqualTo(userId.toString());
        assertThat(forwarded.getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(forwarded.getFirst("X-User-Role")).isEqualTo("USER");
    }

    private String validToken(UUID userId, String email, String role) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .signWith(key)
                .compact();
    }
}
