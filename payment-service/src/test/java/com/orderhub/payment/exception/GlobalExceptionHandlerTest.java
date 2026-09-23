package com.orderhub.payment.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;

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
    void shouldMapPaymentNotFoundTo404() {
        ProblemDetail problem = handler.handlePaymentNotFound(new PaymentNotFoundException(UUID.randomUUID()));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void shouldNotLeakInternalDetailInAServerError() {
        ProblemDetail problem = handler.handleGeneric(
                new IllegalStateException("could not connect to jdbc:postgresql://payment-db:5432"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        // The message can carry hostnames, SQL or credentials — it belongs in the log.
        assertThat(problem.getDetail())
                .isEqualTo("An unexpected error occurred")
                .doesNotContain("jdbc", "payment-db");
    }

    @SuppressWarnings("unused")
    private void stub(String header) {
        // Only exists to give MissingRequestHeaderException a MethodParameter to point at.
    }
}
