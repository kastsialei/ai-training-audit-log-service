package net.sam.ai.engineering.audit.api.ingestion;

import jakarta.validation.Valid;
import net.sam.ai.engineering.audit.application.ingestion.IngestEventCommand;
import net.sam.ai.engineering.audit.application.ingestion.IngestEventUseCase;
import net.sam.ai.engineering.audit.domain.ingestion.AuditEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-events")
public class IngestionController {

    private final IngestEventUseCase useCase;

    public IngestionController(IngestEventUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<IngestEventResponse> ingest(@Valid @RequestBody IngestEventRequest request) {
        AuditEvent saved = useCase.execute(new IngestEventCommand(
                request.actor(),
                request.eventType(),
                request.resource(),
                request.outcome(),
                request.context()));
        return ResponseEntity.status(HttpStatus.CREATED).body(IngestEventResponse.from(saved));
    }
}
