package net.sam.ai.engineering.audit.application.query;

import java.util.List;
import java.util.Optional;
import net.sam.ai.engineering.audit.domain.shared.AuditEvent;
import org.springframework.stereotype.Service;

@Service
public class QueryAuditEventsUseCase {

    private final EventReader reader;
    private final CursorCodec codec;
    private final QueryCriteriaValidator validator;

    public QueryAuditEventsUseCase(EventReader reader, CursorCodec codec, QueryCriteriaValidator validator) {
        this.reader = reader;
        this.codec = codec;
        this.validator = validator;
    }

    public QueryPage execute(QueryCriteria criteria, RequestedLimit limit, Optional<String> rawCursor) {
        validator.validate(criteria);

        String fingerprint = codec.fingerprintFor(criteria);
        Optional<KeysetPosition> position = rawCursor
                .map(raw -> codec.decode(raw, fingerprint))
                .map(decoded -> new KeysetPosition(decoded.rt(), decoded.id()));

        List<AuditEvent> rows = reader.read(criteria, position, limit.value() + 1);

        boolean hasNext = rows.size() > limit.value();
        List<AuditEvent> items = hasNext ? List.copyOf(rows.subList(0, limit.value())) : List.copyOf(rows);

        Optional<String> nextCursor = Optional.empty();
        if (hasNext) {
            AuditEvent last = items.get(items.size() - 1);
            nextCursor = Optional.of(codec.encode(new Cursor(last.getRecordedAt(), last.getId(), fingerprint)));
        }

        return new QueryPage(items, nextCursor);
    }
}
