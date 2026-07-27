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
}
