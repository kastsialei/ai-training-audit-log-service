package net.sam.ai.engineering.audit.infrastructure.persistence.ingestion;

import net.sam.ai.engineering.audit.application.ingestion.EventSink;
import net.sam.ai.engineering.audit.domain.ingestion.AuditEvent;
import org.springframework.stereotype.Component;

@Component
public class JpaEventSink implements EventSink {

    private final AuditEventRepository repository;

    public JpaEventSink(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditEvent append(AuditEvent event) {
        return repository.save(event);
    }
}
