package net.sam.ai.engineering.audit.api.query;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import net.sam.ai.engineering.audit.domain.shared.Outcome;
import org.springframework.web.bind.annotation.BindParam;

public record QueryAuditEventsRequest(
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String actor,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String resource,
        @BindParam("event_type") @Pattern(regexp = ".*\\S.*", message = "must not be blank") String eventType,
        Outcome outcome,
        OffsetDateTime from,
        OffsetDateTime to,
        @Min(value = 1, message = "must be between 1 and 200")
                @Max(value = 200, message = "must be between 1 and 200")
                Integer limit,
        String cursor) {

    @AssertTrue(message = "from must be before or equal to to")
    public boolean isFromBeforeOrEqualTo() {
        if (from == null || to == null) {
            return true;
        }
        return !from.isAfter(to);
    }
}
