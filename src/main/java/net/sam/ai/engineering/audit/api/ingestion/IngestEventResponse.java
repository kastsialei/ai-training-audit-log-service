package net.sam.ai.engineering.audit.api.ingestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;
import net.sam.ai.engineering.audit.domain.shared.AuditEvent;
import net.sam.ai.engineering.audit.domain.shared.Outcome;

public record IngestEventResponse(
        UUID id,
        @JsonProperty("recorded_at") OffsetDateTime recordedAt,
        String actor,
        @JsonProperty("event_type") String eventType,
        String resource,
        Outcome outcome,
        JsonNode context) {

    public static IngestEventResponse from(AuditEvent event) {
        return new IngestEventResponse(
                event.getId(),
                event.getRecordedAt(),
                event.getActor(),
                event.getEventType(),
                event.getResource(),
                event.getOutcome(),
                event.getContext());
    }
}
