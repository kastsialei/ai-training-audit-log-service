package net.sam.ai.engineering.audit.api.query;

import java.time.OffsetDateTime;
import net.sam.ai.engineering.audit.domain.shared.Outcome;
import org.springframework.web.bind.annotation.BindParam;

public record QueryAuditEventsRequest(
        String actor,
        String resource,
        @BindParam("event_type") String eventType,
        Outcome outcome,
        OffsetDateTime from,
        OffsetDateTime to,
        Integer limit,
        String cursor) {}
