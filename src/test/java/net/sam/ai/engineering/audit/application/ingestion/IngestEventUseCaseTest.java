package net.sam.ai.engineering.audit.application.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sam.ai.engineering.audit.domain.ingestion.AuditEvent;
import net.sam.ai.engineering.audit.domain.ingestion.Outcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IngestEventUseCaseTest {

    @Test
    void buildsEventFromCommandAndForwardsToSink() {
        EventSink sink = mock(EventSink.class);
        when(sink.append(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        IngestEventUseCase useCase = new IngestEventUseCase(sink);
        ObjectNode context = JsonNodeFactory.instance.objectNode().put("ip", "10.0.0.1");

        IngestEventCommand command = new IngestEventCommand(
                "alice@example.com", "user.login", "user:42", Outcome.SUCCESS, context);

        AuditEvent result = useCase.execute(command);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(sink).append(captor.capture());
        AuditEvent passed = captor.getValue();

        assertEquals("alice@example.com", passed.getActor());
        assertEquals("user.login", passed.getEventType());
        assertEquals("user:42", passed.getResource());
        assertEquals(Outcome.SUCCESS, passed.getOutcome());
        assertEquals((JsonNode) context, passed.getContext());
        assertSame(passed, result);
    }
}
