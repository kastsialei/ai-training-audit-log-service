package net.sam.ai.engineering.audit.application.query;

import java.util.Optional;

public final class InvalidQueryException extends RuntimeException {

    public enum ProblemKind {
        NO_FILTER,
        BLANK_FILTER,
        INVALID_TIME_RANGE
    }

    private final ProblemKind kind;
    private final String field;

    private InvalidQueryException(ProblemKind kind, String field) {
        super(messageFor(kind));
        this.kind = kind;
        this.field = field;
    }

    public static InvalidQueryException noFilter() {
        return new InvalidQueryException(ProblemKind.NO_FILTER, null);
    }

    public static InvalidQueryException blankFilter(String field) {
        return new InvalidQueryException(ProblemKind.BLANK_FILTER, field);
    }

    public static InvalidQueryException invalidTimeRange() {
        return new InvalidQueryException(ProblemKind.INVALID_TIME_RANGE, null);
    }

    public ProblemKind kind() {
        return kind;
    }

    public Optional<String> field() {
        return Optional.ofNullable(field);
    }

    private static String messageFor(ProblemKind kind) {
        return switch (kind) {
            case NO_FILTER -> "At least one substantive filter is required";
            case BLANK_FILTER -> "Filter value must not be blank";
            case INVALID_TIME_RANGE -> "Time range 'from' must not be after 'to'";
        };
    }
}
