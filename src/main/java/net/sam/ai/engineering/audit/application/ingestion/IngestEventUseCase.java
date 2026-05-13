package net.sam.ai.engineering.audit.application.ingestion;

import net.sam.ai.engineering.audit.domain.shared.AuditEvent;
import org.springframework.stereotype.Service;

@Service
public class IngestEventUseCase {

    private final EventSink sink;

    public IngestEventUseCase(EventSink sink) {
        this.sink = sink;
    }

    public AuditEvent execute(IngestEventCommand command) {
        AuditEvent event = new AuditEvent(
                command.actor(),
                command.eventType(),
                command.resource(),
                command.outcome(),
                command.context());
        return sink.append(event);
    }
}
