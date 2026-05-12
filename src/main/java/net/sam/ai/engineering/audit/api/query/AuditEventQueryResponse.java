package net.sam.ai.engineering.audit.api.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AuditEventQueryResponse(
        List<AuditEventResponse> items,
        @JsonProperty("next_cursor") String nextCursor) {}
