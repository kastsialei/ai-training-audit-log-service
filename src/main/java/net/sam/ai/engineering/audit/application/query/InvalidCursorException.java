package net.sam.ai.engineering.audit.application.query;

public final class InvalidCursorException extends RuntimeException {

    public enum ProblemKind {
        INVALID_CURSOR,
        CURSOR_FILTER_MISMATCH
    }

    private final ProblemKind kind;

    public InvalidCursorException(ProblemKind kind) {
        super(messageFor(kind));
        this.kind = kind;
    }

    public InvalidCursorException(ProblemKind kind, Throwable cause) {
        super(messageFor(kind), cause);
        this.kind = kind;
    }

    public ProblemKind kind() {
        return kind;
    }

    private static String messageFor(ProblemKind kind) {
        return switch (kind) {
            case INVALID_CURSOR -> "cursor is malformed";
            case CURSOR_FILTER_MISMATCH -> "cursor was issued for a different filter set";
        };
    }
}
