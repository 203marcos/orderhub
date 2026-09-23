package com.orderhub.payment.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into an RFC 7807 {@code ProblemDetail}.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} is what keeps client mistakes out of the
 * 5xx bucket. It already maps the whole family of Spring MVC failures — unparsable body, wrong
 * media type, unknown method, a path variable that is not a UUID — to their proper 4xx. Without
 * it those fall through to the catch-all below and get reported as server faults, which is both
 * wrong for the caller and noise in the error dashboards.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(PaymentNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * More specific than the {@code ServletRequestBindingException} the base class would catch,
     * so the caller is told <em>which</em> header is missing.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Missing required header: " + ex.getHeaderName());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        // The message can carry internals (SQL, hostnames), so it goes to the log, not the client.
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}
