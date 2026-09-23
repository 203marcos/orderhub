package com.orderhub.gateway.ratelimit;

import com.orderhub.gateway.filter.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Keys the Redis-backed {@code RequestRateLimiter} by the caller's identity when one is
 * known, falling back to client IP otherwise.
 *
 * <p>By the time this resolver runs, {@link JwtAuthenticationFilter} has already validated
 * the token (on protected routes) and set {@code X-User-Id} from its claims, or left it
 * absent on a public route (e.g. {@code /auth/**}). So:
 *
 * <ul>
 *   <li>Protected API routes: keyed per user — one abusive user cannot exhaust the bucket
 *       shared by everyone else.</li>
 *   <li>Public routes, notably {@code /auth/login}: no user id exists yet, so this falls
 *       back to the client IP, which is what actually protects login from brute-forcing.</li>
 * </ul>
 *
 * <p>Trade-off: Spring's {@code RedisRateLimiter} fails <em>open</em> — if Redis is down,
 * requests pass unlimited rather than the whole API going dark. The gateway's readiness
 * probe includes Redis, so an orchestrator pulls an instance whose limiter lost its
 * backing store out of rotation instead of leaving it serving unthrottled.
 */
@Component
public class UserOrIpKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
            return Mono.just(userId);
        }
        return Mono.just(clientIp(exchange));
    }

    private String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }
}
