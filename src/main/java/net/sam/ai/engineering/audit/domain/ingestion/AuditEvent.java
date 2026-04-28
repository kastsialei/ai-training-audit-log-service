package net.sam.ai.engineering.audit.domain.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private UUID id;

    @Column(name = "recorded_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime recordedAt;

    @Column(name = "actor", nullable = false, updatable = false)
    private String actor;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "resource", nullable = false, updatable = false)
    private String resource;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "outcome", columnDefinition = "audit_outcome", updatable = false)
    private Outcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode context;

    protected AuditEvent() {
        // for JPA
    }

    public AuditEvent(String actor, String eventType, String resource, Outcome outcome, JsonNode context) {
        this.actor = requireNonBlank(actor, "actor");
        this.eventType = requireNonBlank(eventType, "event_type");
        this.resource = requireNonBlank(resource, "resource");
        this.outcome = outcome;
        this.context = requireJsonObject(context);
    }

    private static JsonNode requireJsonObject(JsonNode context) {
        if (context == null || context.isNull() || !context.isObject()) {
            throw new InvalidAuditEventException("context must be a JSON object (use {} for empty)");
        }
        return context;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidAuditEventException(field + " must not be blank");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public String getActor() {
        return actor;
    }

    public String getEventType() {
        return eventType;
    }

    public String getResource() {
        return resource;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public JsonNode getContext() {
        return context;
    }
}
