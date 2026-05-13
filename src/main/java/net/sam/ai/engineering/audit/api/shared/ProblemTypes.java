package net.sam.ai.engineering.audit.api.shared;

import java.net.URI;

final class ProblemTypes {

    private static final String BASE = "https://audit-log-service/problems/";

    static final URI VALIDATION_ERROR = URI.create(BASE + "validation-error");
    static final URI MALFORMED_REQUEST = URI.create(BASE + "malformed-request");
    static final URI INVALID_AUDIT_EVENT = URI.create(BASE + "invalid-audit-event");
    static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");

    static final URI NO_FILTER = URI.create(BASE + "no-filter");
    static final URI BLANK_FILTER = URI.create(BASE + "blank-filter");
    static final URI INVALID_OUTCOME = URI.create(BASE + "invalid-outcome");
    static final URI INVALID_TIMESTAMP = URI.create(BASE + "invalid-timestamp");
    static final URI INVALID_TIME_RANGE = URI.create(BASE + "invalid-time-range");
    static final URI INVALID_LIMIT = URI.create(BASE + "invalid-limit");
    static final URI INVALID_CURSOR = URI.create(BASE + "invalid-cursor");
    static final URI CURSOR_FILTER_MISMATCH = URI.create(BASE + "cursor-filter-mismatch");

    private ProblemTypes() {}
}
