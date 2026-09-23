package com.orderhub.auth.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldMapADuplicateEmailTo409() {
        ProblemDetail problem =
                handler.handleEmailExists(new EmailAlreadyExistsException("marcos@orderhub.com"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void shouldNotRevealWhichHalfOfTheCredentialsWasWrong() {
        ProblemDetail problem =
                handler.handleBadCredentials(new BadCredentialsException("No user found for ghost@orderhub.com"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        // A distinct "unknown email" message would turn login into an account-enumeration oracle.
        assertThat(problem.getDetail())
                .isEqualTo("Invalid email or password")
                .doesNotContain("ghost@orderhub.com");
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
