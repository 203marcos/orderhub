package com.orderhub.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * Validates the JWT once, at the edge, and translates it into identity headers for the
 * services behind it.
 *
 * <p>This is the only place a token is verified. Downstream services never see the JWT — they
 * read {@code X-User-Id}, {@code X-User-Email} and {@code X-User-Role} and trust them. That
 * trust is the whole security model, and it rests on two things:
 *
 * <ol>
 *   <li><b>Those headers are stripped from every inbound request first</b>, so a client cannot
 *       simply send {@code X-User-Id: <someone else>} and be believed. Stripping happens before
 *       the public-path check, because a public route forwards the request untouched and would
 *       otherwise pass a forged header straight through.</li>
 *   <li><b>The services are not reachable from outside</b> — only the gateway publishes a host
 *       port (see docker-compose.yml). Otherwise an attacker skips this filter entirely.</li>
 * </ol>
 *
 * <p>Trusting a header is only safe when both hold. Drop either and the API is open.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_EMAIL_HEADER = "X-User-Email";
    static final String USER_ROLE_HEADER = "X-User-Role";

    /** Headers this filter alone may set. Anything arriving with them is a forgery attempt. */
    private static final List<String> IDENTITY_HEADERS =
            List.of(USER_ID_HEADER, USER_EMAIL_HEADER, USER_ROLE_HEADER);

    private static final List<String> PUBLIC_PATHS = List.of("/auth/");

    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.secret}")
    private String secret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitised = withoutClientSuppliedIdentity(exchange);

        if (isPublic(sanitised.getRequest().getPath().toString())) {
            return chain.filter(sanitised);
        }

        String authHeader = sanitised.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(sanitised);
        }

        try {
            Claims claims = parseToken(authHeader.substring(BEARER_PREFIX.length()));
            return chain.filter(withIdentityOf(sanitised, claims));
        } catch (JwtException ex) {
            // Covers every rejection jjwt can raise — bad signature, expired, malformed. The
            // reason is deliberately not echoed back: it only helps someone probing tokens.
            return unauthorized(sanitised);
        }
    }

    private ServerWebExchange withoutClientSuppliedIdentity(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(request -> request.headers(headers -> IDENTITY_HEADERS.forEach(headers::remove)))
                .build();
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private ServerWebExchange withIdentityOf(ServerWebExchange exchange, Claims claims) {
        return exchange.mutate()
                .request(request -> request
                        .header(USER_ID_HEADER, claims.getSubject())
                        .header(USER_EMAIL_HEADER, claims.get("email", String.class))
                        .header(USER_ROLE_HEADER, claims.get("role", String.class)))
                .build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Runs before Spring Cloud Gateway's routing filters, so no request is proxied to a service
     * before its token has been checked.
     */
    @Override
    public int getOrder() {
        return -1;
    }
}
