package net.sam.ai.engineering.audit.application.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CursorCodec {

    private static final Pattern FP_PATTERN = Pattern.compile("^[0-9a-f]{16}$");

    private final ObjectMapper mapper;
    private final FilterFingerprint fingerprint;

    public CursorCodec(ObjectMapper mapper) {
        this.mapper = mapper
                .copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.fingerprint = new FilterFingerprint(mapper);
    }

    public String encode(Cursor cursor) {
        try {
            byte[] json = mapper.writeValueAsBytes(new Wire(cursor.rt(), cursor.id(), cursor.fp()));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    public Cursor decode(String raw, String expectedFingerprint) {
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException(InvalidCursorException.ProblemKind.INVALID_CURSOR, e);
        }

        Wire wire;
        try {
            wire = mapper.readValue(decoded, Wire.class);
        } catch (IOException e) {
            throw new InvalidCursorException(InvalidCursorException.ProblemKind.INVALID_CURSOR, e);
        }

        if (wire == null || wire.rt() == null || wire.id() == null || wire.fp() == null) {
            throw new InvalidCursorException(InvalidCursorException.ProblemKind.INVALID_CURSOR);
        }
        if (!FP_PATTERN.matcher(wire.fp()).matches()) {
            throw new InvalidCursorException(InvalidCursorException.ProblemKind.INVALID_CURSOR);
        }
        if (!wire.fp().equals(expectedFingerprint)) {
            throw new InvalidCursorException(InvalidCursorException.ProblemKind.CURSOR_FILTER_MISMATCH);
        }
        return new Cursor(wire.rt(), wire.id(), wire.fp());
    }

    public String fingerprintFor(QueryCriteria criteria) {
        return fingerprint.compute(criteria);
    }

    private record Wire(
            @JsonProperty("rt") OffsetDateTime rt,
            @JsonProperty("id") UUID id,
            @JsonProperty("fp") String fp) {
        @JsonCreator
        Wire {}
    }

}
