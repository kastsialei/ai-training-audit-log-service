package net.sam.ai.engineering.audit.api.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.sam.ai.engineering.audit.domain.shared.Outcome;

public record IngestEventRequest(
        @NotBlank String actor,
        @NotBlank @JsonProperty("event_type") String eventType,
        @NotBlank String resource,
        Outcome outcome,
        @NotNull JsonNode context) {

    // Jackson maps JSON `null` to NullNode (not Java null), so @NotNull alone
    // doesn't reject `"context": null`. Require an actual object.
    @JsonIgnore
    @AssertTrue(message = "context must be a JSON object")
    public boolean isContextJsonObject() {
        return context != null && !context.isNull() && context.isObject();
    }
}
