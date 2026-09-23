package com.orderhub.order.exception;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Turns every failure into an RFC 7807 {@code ProblemDetail}.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} is what keeps client mistakes out of the
 * 5xx bucket. It already maps the whole family of Spring MVC failures — unparsable body, wrong
 * media type, unknown method, a path variable that is not a UUID — to their proper 4xx. Without
 * it those fall through to the catch-all below and get reported as server faults, which is both
 * wrong for the caller and noise in the error dashboards.
 *
 * <p>The handlers declared here are for this service's own domain, plus the two framework cases
 * worth a friendlier message than the default.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ProductUnavailableException.class)
    public ProblemDetail handleProductUnavailable(ProductUnavailableException ex) {
        // The request was well formed; the catalog simply cannot fulfil it.
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    public ProblemDetail handleCatalogUnavailable(CatalogUnavailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Catalog service is unavailable");
    }

    @ExceptionHandler(FeignException.class)
    public ProblemDetail handleFeign(FeignException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "A downstream service is unavailable");
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

    /** Overridden, not re-declared: a second handler for this type would be ambiguous. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        // The message can carry internals (SQL, hostnames), so it goes to the log, not the client.
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}
