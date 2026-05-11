package net.sam.ai.engineering.audit.api.shared;

import java.net.URI;

/**
 * Stable problem-type URIs returned in RFC 7807 ProblemDetail responses.
 *
 * <p>URIs are documentation identifiers, not dereferenceable URLs, and must
 * remain stable across releases — clients may match on them.
 *
 * <p>T-10 will extend this with query-specific problem types.
 */
final class ProblemTypes {

    private static final String BASE = "https://audit-log-service/problems/";

    static final URI VALIDATION_ERROR = URI.create(BASE + "validation-error");
    static final URI MALFORMED_REQUEST = URI.create(BASE + "malformed-request");
    static final URI INVALID_AUDIT_EVENT = URI.create(BASE + "invalid-audit-event");
    static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");

    private ProblemTypes() {
        // constants holder
    }
}
