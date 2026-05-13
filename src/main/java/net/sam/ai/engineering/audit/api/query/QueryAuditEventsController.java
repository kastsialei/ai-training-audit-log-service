package net.sam.ai.engineering.audit.api.query;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import net.sam.ai.engineering.audit.application.query.QueryAuditEventsUseCase;
import net.sam.ai.engineering.audit.application.query.QueryCriteria;
import net.sam.ai.engineering.audit.application.query.QueryPage;
import net.sam.ai.engineering.audit.application.query.RequestedLimit;
import net.sam.ai.engineering.audit.application.query.TimeRange;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-events")
public class QueryAuditEventsController {

    private static final int DEFAULT_LIMIT = 50;

    private final QueryAuditEventsUseCase useCase;

    public QueryAuditEventsController(QueryAuditEventsUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public AuditEventQueryResponse query(@Valid @ModelAttribute QueryAuditEventsRequest request) {
        TimeRange timeRange =
                (request.from() == null && request.to() == null) ? null : new TimeRange(request.from(), request.to());
        QueryCriteria criteria = new QueryCriteria(
                request.actor(), request.resource(), request.eventType(), request.outcome(), timeRange);
        RequestedLimit limit = new RequestedLimit(request.limit() == null ? DEFAULT_LIMIT : request.limit());
        Optional<String> rawCursor = Optional.ofNullable(request.cursor());

        QueryPage page = useCase.execute(criteria, limit, rawCursor);

        List<AuditEventResponse> items =
                page.items().stream().map(AuditEventResponse::from).toList();
        return new AuditEventQueryResponse(items, page.nextCursor().orElse(null));
    }
}
