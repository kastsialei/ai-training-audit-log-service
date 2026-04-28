package net.sam.ai.engineering.audit.domain.ingestion;

public class InvalidAuditEventException extends RuntimeException {
    public InvalidAuditEventException(String message) {
        super(message);
    }
}
