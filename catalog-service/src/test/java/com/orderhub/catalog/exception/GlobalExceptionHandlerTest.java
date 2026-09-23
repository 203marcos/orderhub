package com.orderhub.catalog.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldMapAMissingProductTo404WithItsId() {
        UUID id = UUID.randomUUID();

        ProblemDetail problem = handler.handleProductNotFound(new ProductNotFoundException(id));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).contains(id.toString());
    }

    @Test
    void shouldMapAnUnexpectedFailureTo500WithoutLeakingItsMessage() {
        ProblemDetail problem =
                handler.handleGeneric(new IllegalStateException("connection to jdbc:postgresql://db-internal:5432 refused"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        // Internals (hostnames, SQL, stack details) belong in the log, never in the response body.
        assertThat(problem.getDetail())
                .isEqualTo("An unexpected error occurred")
                .doesNotContain("jdbc:postgresql", "db-internal");
    }
}
