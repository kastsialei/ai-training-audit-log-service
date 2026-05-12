package net.sam.ai.engineering.audit.api.shared;

import java.net.URI;

final class ProblemTypes {

    private static final String BASE = "https://audit-log-service/problems/";

    static final URI VALIDATION_ERROR = URI.create(BASE + "validation-error");
    static final URI MALFORMED_REQUEST = URI.create(BASE + "malformed-request");
    static final URI INVALID_AUDIT_EVENT = URI.create(BASE + "invalid-audit-event");
    static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");

    private ProblemTypes() {}
}
