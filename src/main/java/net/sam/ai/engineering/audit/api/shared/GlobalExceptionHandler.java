package net.sam.ai.engineering.audit.api.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import net.sam.ai.engineering.audit.domain.ingestion.InvalidAuditEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(GlobalExceptionHandler::formatFieldError)
                .orElse("Request body is invalid");
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_ERROR, "Invalid request", detail, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String detail = ex.getName() + " has an invalid value";
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_ERROR, "Invalid request", detail, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String detail = ex.getParameterName() + " is required";
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.MALFORMED_REQUEST, "Invalid request", detail, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.MALFORMED_REQUEST,
                "Invalid request",
                "Request body is malformed",
                request);
    }

    @ExceptionHandler(InvalidAuditEventException.class)
    public ResponseEntity<ProblemDetail> handleInvalidAuditEvent(
            InvalidAuditEventException ex, HttpServletRequest request) {
        log.warn("Invalid audit event payload: {}", ex.getMessage());
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.INVALID_AUDIT_EVENT,
                "Invalid audit event",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.INTERNAL_ERROR,
                "Internal server error",
                "An unexpected error occurred.",
                request);
    }

    private static String formatFieldError(FieldError fe) {
        String message = fe.getDefaultMessage();
        return message == null ? fe.getField() + " is invalid" : fe.getField() + " " + message;
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, URI type, String title, String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(type);
        body.setTitle(title);
        body.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
