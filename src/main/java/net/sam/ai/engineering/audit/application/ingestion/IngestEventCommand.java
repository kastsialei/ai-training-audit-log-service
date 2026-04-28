package net.sam.ai.engineering.audit.application.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import net.sam.ai.engineering.audit.domain.ingestion.Outcome;

public record IngestEventCommand(
        String actor, String eventType, String resource, Outcome outcome, JsonNode context) {}
