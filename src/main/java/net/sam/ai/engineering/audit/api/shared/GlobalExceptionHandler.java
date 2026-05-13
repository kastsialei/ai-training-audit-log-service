package net.sam.ai.engineering.audit.api.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import net.sam.ai.engineering.audit.api.query.QueryAuditEventsRequest;
import net.sam.ai.engineering.audit.application.query.InvalidCursorException;
import net.sam.ai.engineering.audit.application.query.InvalidQueryException;
import net.sam.ai.engineering.audit.domain.ingestion.InvalidAuditEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Spring's default model-attribute name for the query DTO. Used to detect
    // the binding owner when the partially-bound target is null (e.g. when
    // type conversion fails before record construction).
    private static final String QUERY_DTO_OBJECT_NAME = "queryAuditEventsRequest";

    // Constant detail strings for cursor errors. Never include exception messages
    // or user-supplied input — by glossary §4, cursor leaks must not surface.
    private static final Map<InvalidCursorException.ProblemKind, String> CURSOR_DETAIL = Map.of(
            InvalidCursorException.ProblemKind.INVALID_CURSOR, "cursor is malformed",
            InvalidCursorException.ProblemKind.CURSOR_FILTER_MISMATCH,
                    "cursor was issued for a different filter set");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        FieldError fieldError =
                ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        ObjectError globalError =
                ex.getBindingResult().getGlobalErrors().stream().findFirst().orElse(null);
        // Target may be null when binding fails before construction (e.g. type
        // mismatch on a record component), so match on the binding-result
        // object name instead of `instanceof` on the partially-bound target.
        boolean queryDto = QUERY_DTO_OBJECT_NAME.equals(ex.getBindingResult().getObjectName())
                || ex.getBindingResult().getTarget() instanceof QueryAuditEventsRequest;
        URI type = queryDto ? problemTypeForQueryField(fieldError) : ProblemTypes.VALIDATION_ERROR;
        String detail = fieldError != null
                ? formatFieldError(fieldError)
                : (globalError != null ? formatGlobalError(globalError) : "Request body is invalid");
        return problem(HttpStatus.BAD_REQUEST, type, "Invalid request", detail, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        URI type = problemTypeFor(ex);
        String detail = detailFor(type, ex.getName());
        return problem(HttpStatus.BAD_REQUEST, type, "Invalid request", detail, request);
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

    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<ProblemDetail> handleInvalidQuery(
            InvalidQueryException ex, HttpServletRequest request) {
        URI type = switch (ex.kind()) {
            case NO_FILTER -> ProblemTypes.NO_FILTER;
            case BLANK_FILTER -> ProblemTypes.BLANK_FILTER;
            case INVALID_TIME_RANGE -> ProblemTypes.INVALID_TIME_RANGE;
        };
        return problem(HttpStatus.BAD_REQUEST, type, "Invalid request", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCursor(
            InvalidCursorException ex, HttpServletRequest request) {
        URI type = switch (ex.kind()) {
            case INVALID_CURSOR -> ProblemTypes.INVALID_CURSOR;
            case CURSOR_FILTER_MISMATCH -> ProblemTypes.CURSOR_FILTER_MISMATCH;
        };
        String detail = CURSOR_DETAIL.get(ex.kind());
        return problem(HttpStatus.BAD_REQUEST, type, "Invalid request", detail, request);
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

    private static URI problemTypeFor(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        if ("outcome".equals(name)) {
            return ProblemTypes.INVALID_OUTCOME;
        }
        if ("from".equals(name) || "to".equals(name)) {
            return ProblemTypes.INVALID_TIMESTAMP;
        }
        return ProblemTypes.VALIDATION_ERROR;
    }

    private static URI problemTypeForQueryField(FieldError fieldError) {
        if (fieldError == null) {
            return ProblemTypes.VALIDATION_ERROR;
        }
        String field = fieldError.getField();
        if ("limit".equals(field)) {
            return ProblemTypes.INVALID_LIMIT;
        }
        if ("actor".equals(field) || "resource".equals(field) || "eventType".equals(field)) {
            return ProblemTypes.BLANK_FILTER;
        }
        if ("fromBeforeOrEqualTo".equals(field)) {
            return ProblemTypes.INVALID_TIME_RANGE;
        }
        if (isTypeMismatch(fieldError)) {
            if ("outcome".equals(field)) {
                return ProblemTypes.INVALID_OUTCOME;
            }
            if ("from".equals(field) || "to".equals(field)) {
                return ProblemTypes.INVALID_TIMESTAMP;
            }
        }
        return ProblemTypes.VALIDATION_ERROR;
    }

    private static boolean isTypeMismatch(FieldError fieldError) {
        String[] codes = fieldError.getCodes();
        if (codes == null) {
            return false;
        }
        for (String code : codes) {
            if ("typeMismatch".equals(code)) {
                return true;
            }
        }
        return false;
    }

    private static String detailFor(URI type, String name) {
        if (ProblemTypes.INVALID_OUTCOME.equals(type)) {
            return "outcome has an invalid value";
        }
        if (ProblemTypes.INVALID_TIMESTAMP.equals(type)) {
            return name + " is not a valid ISO-8601 timestamp with offset";
        }
        return name + " has an invalid value";
    }

    private static String formatFieldError(FieldError fe) {
        String message = fe.getDefaultMessage();
        return message == null ? fe.getField() + " is invalid" : fe.getField() + " " + message;
    }

    private static String formatGlobalError(ObjectError ge) {
        String message = ge.getDefaultMessage();
        return message == null ? "Request is invalid" : message;
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
