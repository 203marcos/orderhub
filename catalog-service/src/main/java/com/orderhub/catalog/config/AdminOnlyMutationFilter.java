package com.orderhub.catalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Gateway-trusted identity (see ARCHITECTURE.md, "Gateway-trusted identity"): api-gateway
 * validates the JWT and forwards {@code X-User-Role}; catalog-service only has to enforce that
 * mutating a product requires the ADMIN role — a plain header check, not Spring Security,
 * consistent with how the rest of the codebase trusts the forwarded identity headers instead
 * of re-parsing tokens.
 *
 * <p>Scoped to {@code /api/v1/products} and to the mutating verbs only, so GET stays open to
 * any authenticated caller.
 */
@Component
public class AdminOnlyMutationFilter extends OncePerRequestFilter {

    private static final String PRODUCTS_PATH = "/api/v1/products";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE");

    private final ObjectMapper objectMapper;

    public AdminOnlyMutationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (isMutatingProductsRequest(request) && !ADMIN_ROLE.equals(request.getHeader(ROLE_HEADER))) {
            respondForbidden(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isMutatingProductsRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith(PRODUCTS_PATH) && MUTATING_METHODS.contains(request.getMethod());
    }

    private void respondForbidden(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "This operation requires the ADMIN role");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
