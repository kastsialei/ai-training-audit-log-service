package net.sam.ai.engineering.audit.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import net.sam.ai.engineering.audit.application.query.InvalidCursorException.ProblemKind;
import net.sam.ai.engineering.audit.domain.shared.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CursorCodecTest {

    private static final String FP_A = "0123456789abcdef";
    private static final String FP_B = "fedcba9876543210";

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final CursorCodec codec = new CursorCodec(mapper);

    @Test
    void roundTrip_preservesAllFields() {
        Cursor original = new Cursor(
                OffsetDateTime.of(2026, 4, 30, 14, 22, 1, 123_456_000, ZoneOffset.UTC),
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                FP_A);

        Cursor decoded = codec.decode(codec.encode(original), FP_A);

        assertThat(decoded.id()).isEqualTo(original.id());
        assertThat(decoded.fp()).isEqualTo(original.fp());
        assertThat(decoded.rt().toInstant()).isEqualTo(original.rt().toInstant());
    }

    @Test
    void decode_rejectsBadBase64() {
        assertThatThrownBy(() -> codec.decode("!!!not-base64!!!", FP_A))
                .isInstanceOf(InvalidCursorException.class)
                .extracting(e -> ((InvalidCursorException) e).kind())
                .isEqualTo(ProblemKind.INVALID_CURSOR);
    }

    @Test
    void decode_rejectsBadJson() {
        String badJson = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("not-json".getBytes(StandardCharsets.UTF_8));

        InvalidCursorException ex = catchInvalid(() -> codec.decode(badJson, FP_A));

        assertThat(ex.kind()).isEqualTo(ProblemKind.INVALID_CURSOR);
        assertThat(ex.getMessage()).isEqualTo("cursor is malformed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"id\":\"11111111-2222-3333-4444-555555555555\",\"fp\":\"0123456789abcdef\"}",
            "{\"rt\":\"2026-04-30T14:22:01Z\",\"fp\":\"0123456789abcdef\"}",
            "{\"rt\":\"2026-04-30T14:22:01Z\",\"id\":\"11111111-2222-3333-4444-555555555555\"}"
    })
    void decode_rejectsMissingField(String json) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        InvalidCursorException ex = catchInvalid(() -> codec.decode(encoded, FP_A));

        assertThat(ex.kind()).isEqualTo(ProblemKind.INVALID_CURSOR);
    }

    @Test
    void decode_rejectsBadUuid() {
        String json = "{\"rt\":\"2026-04-30T14:22:01Z\",\"id\":\"not-a-uuid\",\"fp\":\"" + FP_A + "\"}";
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        InvalidCursorException ex = catchInvalid(() -> codec.decode(encoded, FP_A));

        assertThat(ex.kind()).isEqualTo(ProblemKind.INVALID_CURSOR);
        assertThat(ex.getMessage()).doesNotContain("not-a-uuid");
    }

    @Test
    void decode_rejectsBadTimestamp() {
        String json = "{\"rt\":\"2026-13-40T99:99:99Z\",\"id\":\"11111111-2222-3333-4444-555555555555\",\"fp\":\""
                + FP_A + "\"}";
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        InvalidCursorException ex = catchInvalid(() -> codec.decode(encoded, FP_A));

        assertThat(ex.kind()).isEqualTo(ProblemKind.INVALID_CURSOR);
        assertThat(ex.getMessage()).doesNotContain("2026-13-40");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "zzzzzzzzzzzzzzzz", "0123456789ABCDEF", "0123456789abcdef0"})
    void decode_rejectsBadFingerprintFormat(String badFp) {
        String json = "{\"rt\":\"2026-04-30T14:22:01Z\",\"id\":\"11111111-2222-3333-4444-555555555555\",\"fp\":\""
                + badFp + "\"}";
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        InvalidCursorException ex = catchInvalid(() -> codec.decode(encoded, badFp));

        assertThat(ex.kind()).isEqualTo(ProblemKind.INVALID_CURSOR);
    }

    @Test
    void decode_rejectsFilterMismatch() {
        Cursor cursor = new Cursor(
                OffsetDateTime.of(2026, 4, 30, 14, 22, 1, 0, ZoneOffset.UTC),
                UUID.randomUUID(),
                FP_A);

        InvalidCursorException ex = catchInvalid(() -> codec.decode(codec.encode(cursor), FP_B));

        assertThat(ex.kind()).isEqualTo(ProblemKind.CURSOR_FILTER_MISMATCH);
        assertThat(ex.getMessage()).isEqualTo("cursor was issued for a different filter set");
    }

    @Test
    void fingerprintFor_matchesAcrossEqualCriteria() {
        QueryCriteria a = new QueryCriteria("u_42", "doc:9821", null, null, null);
        QueryCriteria b = new QueryCriteria("u_42", "doc:9821", null, null, null);

        assertThat(codec.fingerprintFor(a)).isEqualTo(codec.fingerprintFor(b));
        assertThat(codec.fingerprintFor(a)).matches("^[0-9a-f]{16}$");
    }

    @Test
    void fingerprintFor_differsWhenAnyFilterChanges() {
        QueryCriteria base = new QueryCriteria("u_42", null, null, null, null);
        QueryCriteria changed = new QueryCriteria("u_43", null, null, null, null);

        assertThat(codec.fingerprintFor(base)).isNotEqualTo(codec.fingerprintFor(changed));
    }

    @Test
    void fingerprintFor_outcomeUppercased() {
        QueryCriteria withOutcome = new QueryCriteria(null, null, null, Outcome.DENIED, null);

        assertThat(codec.fingerprintFor(withOutcome)).matches("^[0-9a-f]{16}$");
    }

    @Test
    void fingerprintFor_timestampsNormalizedToUtc() {
        OffsetDateTime plusTwo = OffsetDateTime.of(2026, 4, 30, 16, 22, 1, 0, ZoneOffset.ofHours(2));
        OffsetDateTime utc = OffsetDateTime.of(2026, 4, 30, 14, 22, 1, 0, ZoneOffset.UTC);
        QueryCriteria a = new QueryCriteria("u_42", null, null, null, new TimeRange(plusTwo, null));
        QueryCriteria b = new QueryCriteria("u_42", null, null, null, new TimeRange(utc, null));

        assertThat(codec.fingerprintFor(a)).isEqualTo(codec.fingerprintFor(b));
    }

    @Test
    void fingerprintFor_stringsAreByteExactNoTrim() {
        QueryCriteria untrimmed = new QueryCriteria(" u_42 ", null, null, null, null);
        QueryCriteria trimmed = new QueryCriteria("u_42", null, null, null, null);

        assertThat(codec.fingerprintFor(untrimmed)).isNotEqualTo(codec.fingerprintFor(trimmed));
    }

    @Test
    void invalidCursorException_doesNotLeakInputOrDecodedFields() {
        String badCursor = "!!!secret-cursor-payload!!!";

        InvalidCursorException ex = catchInvalid(() -> codec.decode(badCursor, FP_A));

        assertThat(ex.getMessage()).doesNotContain("secret-cursor-payload");
    }

    @Test
    void exceptionExposesStableProblemKindEnum() {
        assertThat(ProblemKind.values())
                .containsExactlyInAnyOrder(ProblemKind.INVALID_CURSOR, ProblemKind.CURSOR_FILTER_MISMATCH);
    }

    private static InvalidCursorException catchInvalid(Runnable r) {
        try {
            r.run();
        } catch (InvalidCursorException e) {
            return e;
        }
        throw new AssertionError("Expected InvalidCursorException was not thrown");
    }
}
