package net.sam.ai.engineering.audit.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.sam.ai.engineering.audit.domain.shared.AuditEvent;
import net.sam.ai.engineering.audit.domain.shared.Outcome;
import org.junit.jupiter.api.Test;

class QueryAuditEventsUseCaseTest {

    private static final OffsetDateTime T1 = OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = OffsetDateTime.of(2026, 5, 1, 11, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T3 = OffsetDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final CursorCodec codec = new CursorCodec(mapper);
    private final QueryCriteriaValidator validator = new QueryCriteriaValidator();
    private final FakeEventReader fake = new FakeEventReader();
    private final QueryAuditEventsUseCase useCase = new QueryAuditEventsUseCase(fake, codec, validator);

    @Test
    void passesCriteriaAndKeysetThroughToReader_whenCursorMatches() {
        QueryCriteria criteria = new QueryCriteria("u_42", null, null, null, new TimeRange(T1, T3));
        OffsetDateTime anchorRt = OffsetDateTime.of(2026, 5, 1, 9, 30, 0, 0, ZoneOffset.UTC);
        UUID anchorId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String rawCursor = codec.encode(new Cursor(anchorRt, anchorId, codec.fingerprintFor(criteria)));

        useCase.execute(criteria, new RequestedLimit(50), Optional.of(rawCursor));

        assertThat(fake.callCount).isEqualTo(1);
        assertThat(fake.lastCriteria).isSameAs(criteria);
        assertThat(fake.lastPosition).isEqualTo(Optional.of(new KeysetPosition(anchorRt, anchorId)));
        assertThat(fake.lastRowLimit).isEqualTo(51);
    }

    @Test
    void dropsOvershootRow_andEncodesNextCursorFromLastReturnedItem() {
        QueryCriteria criteria = new QueryCriteria("u_42", null, null, null, null);
        AuditEvent e1 = event(T3, UUID.randomUUID());
        AuditEvent e2 = event(T2, UUID.randomUUID());
        AuditEvent e3 = event(T1, UUID.randomUUID());
        fake.toReturn = List.of(e1, e2, e3);

        QueryPage page = useCase.execute(criteria, new RequestedLimit(2), Optional.empty());

        assertThat(page.items()).containsExactly(e1, e2);
        assertThat(page.nextCursor()).isPresent();

        Cursor decoded = codec.decode(page.nextCursor().get(), codec.fingerprintFor(criteria));
        assertThat(decoded.id()).isEqualTo(e2.getId());
        assertThat(decoded.rt().toInstant()).isEqualTo(e2.getRecordedAt().toInstant());
        assertThat(decoded.fp()).isEqualTo(codec.fingerprintFor(criteria));
    }

    @Test
    void returnsEmptyNextCursor_whenReaderReturnsExactlyLimit() {
        QueryCriteria criteria = new QueryCriteria("u_42", null, null, null, null);
        AuditEvent e1 = event(T2, UUID.randomUUID());
        AuditEvent e2 = event(T1, UUID.randomUUID());
        fake.toReturn = List.of(e1, e2);

        QueryPage page = useCase.execute(criteria, new RequestedLimit(2), Optional.empty());

        assertThat(page.items()).containsExactly(e1, e2);
        assertThat(page.nextCursor()).isEmpty();
    }

    @Test
    void returnsEmptyNextCursor_whenReaderReturnsFewerThanLimit() {
        QueryCriteria criteria = new QueryCriteria("u_42", null, null, null, null);
        fake.toReturn = List.of();

        QueryPage page = useCase.execute(criteria, new RequestedLimit(50), Optional.empty());

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isEmpty();
    }

    @Test
    void rejectsCursor_whenFingerprintMismatch_andDoesNotCallReader() {
        QueryCriteria criteriaA = new QueryCriteria("u_42", null, null, null, null);
        QueryCriteria criteriaB = new QueryCriteria("u_43", null, null, null, null);
        String rawA = codec.encode(new Cursor(T2, UUID.randomUUID(), codec.fingerprintFor(criteriaA)));

        assertThatThrownBy(() -> useCase.execute(criteriaB, new RequestedLimit(50), Optional.of(rawA)))
                .isInstanceOf(InvalidCursorException.class)
                .extracting(e -> ((InvalidCursorException) e).kind())
                .isEqualTo(InvalidCursorException.ProblemKind.CURSOR_FILTER_MISMATCH);

        assertThat(fake.callCount).isZero();
    }

    @Test
    void rejectsCriteria_whenNoSubstantiveFilterIsPresent() {
        QueryCriteria criteria = new QueryCriteria(null, null, null, null, null);

        assertThatThrownBy(() -> useCase.execute(criteria, new RequestedLimit(50), Optional.empty()))
                .isInstanceOf(InvalidQueryException.class)
                .extracting(e -> ((InvalidQueryException) e).kind())
                .isEqualTo(InvalidQueryException.ProblemKind.NO_FILTER);

        assertThat(fake.callCount).isZero();
    }

    @Test
    void propagatesInvalidCursor_fromCodec_withoutCallingReader() {
        QueryCriteria criteria = new QueryCriteria("u_42", null, null, null, null);

        assertThatThrownBy(() -> useCase.execute(criteria, new RequestedLimit(50), Optional.of("!!!not-base64!!!")))
                .isInstanceOf(InvalidCursorException.class)
                .extracting(e -> ((InvalidCursorException) e).kind())
                .isEqualTo(InvalidCursorException.ProblemKind.INVALID_CURSOR);

        assertThat(fake.callCount).isZero();
    }

    private static AuditEvent event(OffsetDateTime recordedAt, UUID id) {
        AuditEvent event = new AuditEvent("u", "type", "res", Outcome.SUCCESS, JsonNodeFactory.instance.objectNode());
        setField(event, "id", id);
        setField(event, "recordedAt", recordedAt);
        return event;
    }

    private static void setField(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot set field " + field, e);
        }
    }

    private static final class FakeEventReader implements EventReader {
        QueryCriteria lastCriteria;
        Optional<KeysetPosition> lastPosition;
        int lastRowLimit;
        int callCount;
        List<AuditEvent> toReturn = new ArrayList<>();

        @Override
        public List<AuditEvent> read(QueryCriteria criteria, Optional<KeysetPosition> position, int rowLimit) {
            this.lastCriteria = criteria;
            this.lastPosition = position;
            this.lastRowLimit = rowLimit;
            this.callCount++;
            return toReturn;
        }
    }
}
