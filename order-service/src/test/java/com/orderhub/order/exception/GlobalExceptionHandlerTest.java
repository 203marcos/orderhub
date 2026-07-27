package com.orderhub.order.exception;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReportAMissingIdentityHeaderAsBadRequestNotServerError() throws Exception {
        MissingRequestHeaderException ex = new MissingRequestHeaderException(
                "X-User-Id", new MethodParameter(getClass().getDeclaredMethod("stub", String.class), 0));

        ProblemDetail problem = handler.handleMissingHeader(ex);

        // A caller that forgot the gateway's header made a bad request; the catch-all below
        // would otherwise blame the server for it.
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).contains("X-User-Id");
    }

    @Test
    void shouldMapOrderNotFoundTo404() {
        ProblemDetail problem = handler.handleOrderNotFound(new OrderNotFoundException(UUID.randomUUID()));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void shouldMapAnUnavailableProductTo422() {
        ProblemDetail problem =
                handler.handleProductUnavailable(new ProductUnavailableException(UUID.randomUUID()));

        // The request was well formed; the catalog simply cannot fulfil it.
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
    }

    @Test
    void shouldMapAnUnreachableCatalogTo503WithoutNamingIt() {
        ProblemDetail problem = handler.handleCatalogUnavailable(new CatalogUnavailableException(
                UUID.randomUUID(), new RuntimeException("connection refused to catalog:8082")));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getDetail()).doesNotContain("8082");
    }

    @Test
    void shouldMapAnyOtherFeignFailureTo503WithoutNamingTheDownstream() {
        // Not just the mapped clients' own exceptions (they fall back before reaching here):
        // any FeignException that does escape must still read as "downstream unavailable",
        // never as this service's own fault.
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/payments/orders/x",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        FeignException ex = new FeignException.ServiceUnavailable(
                "payment-service down", request, null, Collections.emptyMap());

        ProblemDetail problem = handler.handleFeign(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getDetail())
                .isEqualTo("A downstream service is unavailable")
                .doesNotContain("payment-service");
    }

    @Test
    void shouldNotLeakInternalDetailInAServerError() {
        ProblemDetail problem = handler.handleGeneric(
                new IllegalStateException("could not connect to jdbc:postgresql://order-db:5432"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        // The message can carry hostnames, SQL or credentials — it belongs in the log.
        assertThat(problem.getDetail())
                .isEqualTo("An unexpected error occurred")
                .doesNotContain("jdbc", "order-db");
    }

    @SuppressWarnings("unused")
    private void stub(String header) {
        // Only exists to give MissingRequestHeaderException a MethodParameter to point at.
    }
}
