package net.sam.ai.engineering.audit.api.shared;

import java.util.Arrays;
import net.sam.ai.engineering.audit.api.query.QueryAuditEventsRequest;
import org.junit.jupiter.api.Test;

/**
 * Pins the DTO `limit` component name used by the field-name switch in
 * {@link GlobalExceptionHandler}. A silent rename would re-route limit
 * failures to the generic `validation-error` and break the `invalid-limit`
 * contract (glossary §5, design §4).
 */
class GlobalExceptionHandlerLimitFieldNameTest {

    @Test
    void limitComponentName_pinsInvalidLimitRouting() {
        boolean hasLimit = Arrays.stream(QueryAuditEventsRequest.class.getRecordComponents())
                .anyMatch(c -> "limit".equals(c.getName()));
        org.junit.jupiter.api.Assertions.assertTrue(
                hasLimit,
                "QueryAuditEventsRequest must expose a record component named 'limit' to keep "
                        + "MethodArgumentNotValidException -> invalid-limit routing stable");
    }
}
