package net.sam.ai.engineering.audit.domain.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

class AuditEventTest {

    private static final JsonNode EMPTY_CONTEXT = JsonNodeFactory.instance.objectNode();

    @Test
    void rejectsNullActor() {
        assertThrows(InvalidAuditEventException.class,
                () -> new AuditEvent(null, "user.login", "user:1", Outcome.SUCCESS, EMPTY_CONTEXT));
    }

    @Test
    void rejectsBlankActor() {
        assertThrows(InvalidAuditEventException.class,
                () -> new AuditEvent("  ", "user.login", "user:1", Outcome.SUCCESS, EMPTY_CONTEXT));
    }

    @Test
    void rejectsBlankEventType() {
        assertThrows(InvalidAuditEventException.class,
                () -> new AuditEvent("alice@x", "", "user:1", Outcome.SUCCESS, EMPTY_CONTEXT));
    }

    @Test
    void rejectsBlankResource() {
        assertThrows(InvalidAuditEventException.class,
                () -> new AuditEvent("alice@x", "user.login", null, Outcome.SUCCESS, EMPTY_CONTEXT));
    }

    @Test
    void rejectsNullContext() {
        assertThrows(InvalidAuditEventException.class,
                () -> new AuditEvent("alice@x", "user.login", "user:1", Outcome.SUCCESS, null));
    }

    @Test
    void rejectsJsonNullContext() {
        assertThrows(InvalidAuditEventException.class, () -> new AuditEvent(
                "alice@x", "user.login", "user:1", Outcome.SUCCESS, JsonNodeFactory.instance.nullNode()));
    }

    @Test
    void rejectsNonObjectContext() {
        assertThrows(InvalidAuditEventException.class, () -> new AuditEvent(
                "alice@x", "user.login", "user:1", Outcome.SUCCESS, JsonNodeFactory.instance.arrayNode()));
    }

    @Test
    void allowsNullOutcome() {
        AuditEvent event = new AuditEvent("alice@x", "user.login", "user:1", null, EMPTY_CONTEXT);

        assertNull(event.getOutcome());
        assertEquals("alice@x", event.getActor());
        assertEquals("user.login", event.getEventType());
        assertEquals("user:1", event.getResource());
        assertEquals(EMPTY_CONTEXT, event.getContext());
    }
}
