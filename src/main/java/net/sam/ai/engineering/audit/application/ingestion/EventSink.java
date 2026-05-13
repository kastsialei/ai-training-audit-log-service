package net.sam.ai.engineering.audit.application.ingestion;

import net.sam.ai.engineering.audit.domain.shared.AuditEvent;

public interface EventSink {
    AuditEvent append(AuditEvent event);
}
