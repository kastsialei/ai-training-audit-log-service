package net.sam.ai.engineering.audit.api.shared;

import jakarta.servlet.http.HttpServletRequest;
import net.sam.ai.engineering.audit.domain.ingestion.InvalidAuditEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps unhandled framework and domain exceptions to RFC 7807 ProblemDetail
 * responses with {@code Content-Type: application/problem+json}.
 *
 * <p>Bad client input (validation, binding, malformed body, domain-invariant
 * violation) maps to {@code 400}. Anything else falls through to the safe
 * catch-all that returns {@code 500} with a generic detail — exception
 * messages and stack frames never reach the response body.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TITLE_INVALID_REQUEST = "Invalid request";
    private static final String TITLE_INTERNAL_ERROR = "Internal server error";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + (fe.getDefaultMessage() == null ? "is invalid" : fe.getDefaultMessage()))
                .orElse("request validation failed");
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_ERROR, TITLE_INVALID_REQUEST, detail, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String detail = ex.getName() + " has an invalid value";
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_ERROR, TITLE_INVALID_REQUEST, detail, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String detail = "required parameter '" + ex.getParameterName() + "' is missing";
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.MALFORMED_REQUEST, TITLE_INVALID_REQUEST, detail, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Detail is generic — the underlying parser message can include payload fragments.
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.MALFORMED_REQUEST,
                TITLE_INVALID_REQUEST,
                "request body is malformed",
                request);
    }

    @ExceptionHandler(InvalidAuditEventException.class)
    ResponseEntity<ProblemDetail> handleInvalidAuditEvent(
            InvalidAuditEventException ex, HttpServletRequest request) {
        // Domain message is curated (no user data echoed); safe to surface as detail.
        log.warn("Invalid audit event payload: {}", ex.getMessage());
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.INVALID_AUDIT_EVENT,
                TITLE_INVALID_REQUEST,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleAny(Exception ex, HttpServletRequest request) {
        // Log the exception server-side; never echo its message or class to the client.
        log.error("Unhandled exception", ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.INTERNAL_ERROR,
                TITLE_INTERNAL_ERROR,
                "An unexpected error occurred.",
                request);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, java.net.URI type, String title, String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(type);
        body.setTitle(title);
        body.setInstance(java.net.URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
