package net.sam.ai.engineering.audit.api.query;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import net.sam.ai.engineering.audit.domain.shared.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class QueryAuditEventsIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager em;

    @Autowired
    ObjectMapper objectMapper;

    private static final OffsetDateTime T = OffsetDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void clean() {
        em.createNativeQuery("DELETE FROM audit_events").executeUpdate();
    }

    @Test
    void getFilteredQuery_returns200ApplicationJson() throws Exception {
        seed(UUID.randomUUID(), T, "u_42", "doc.read", "doc:1", Outcome.SUCCESS);

        mockMvc.perform(get("/audit-events").param("actor", "u_42"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void response_usesSnakeCaseEnvelopeAndItemFields() throws Exception {
        UUID id = UUID.randomUUID();
        seed(id, T, "u_42", "doc.read", "doc:9821", Outcome.SUCCESS);

        String body = mockMvc.perform(get("/audit-events").param("actor", "u_42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.next_cursor", nullValue()))
                .andExpect(jsonPath("$.items[0].id", equalTo(id.toString())))
                .andExpect(jsonPath("$.items[0].recorded_at", notNullValue()))
                .andExpect(jsonPath("$.items[0].actor", equalTo("u_42")))
                .andExpect(jsonPath("$.items[0].event_type", equalTo("doc.read")))
                .andExpect(jsonPath("$.items[0].resource", equalTo("doc:9821")))
                .andExpect(jsonPath("$.items[0].outcome", equalTo("SUCCESS")))
                .andExpect(jsonPath("$.items[0].context", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = objectMapper.readValue(body, Map.class);
        org.junit.jupiter.api.Assertions.assertFalse(envelope.containsKey("nextCursor"));
        @SuppressWarnings("unchecked")
        Map<String, Object> item =
                ((java.util.List<Map<String, Object>>) envelope.get("items")).get(0);
        org.junit.jupiter.api.Assertions.assertFalse(item.containsKey("recordedAt"));
        org.junit.jupiter.api.Assertions.assertFalse(item.containsKey("eventType"));
    }

    @Test
    void noMatches_returnsEmptyItemsAndNullCursor() throws Exception {
        mockMvc.perform(get("/audit-events").param("actor", "does-not-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.next_cursor", nullValue()));
    }

    @Test
    void multipleFilters_appliedAsAnd() throws Exception {
        UUID match = UUID.randomUUID();
        seed(match, T, "u_1", "doc.read", "doc:1", Outcome.SUCCESS);
        seed(UUID.randomUUID(), T, "u_1", "doc.read", "doc:2", Outcome.SUCCESS);
        seed(UUID.randomUUID(), T, "u_2", "doc.read", "doc:1", Outcome.SUCCESS);

        mockMvc.perform(get("/audit-events").param("actor", "u_1").param("resource", "doc:1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", equalTo(match.toString())));
    }

    @Test
    void responseOrder_isRecordedAtDescThenIdDesc() throws Exception {
        UUID newestHigh = UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID newestLow = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID olderHigh = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID olderLow = UUID.fromString("00000000-0000-0000-0000-000000000001");
        seed(newestHigh, T.plusSeconds(10), "u", "t", "r", Outcome.SUCCESS);
        seed(newestLow, T.plusSeconds(10), "u", "t", "r", Outcome.SUCCESS);
        seed(olderHigh, T, "u", "t", "r", Outcome.SUCCESS);
        seed(olderLow, T, "u", "t", "r", Outcome.SUCCESS);

        mockMvc.perform(get("/audit-events").param("actor", "u"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].id", equalTo(newestHigh.toString())))
                .andExpect(jsonPath("$.items[1].id", equalTo(newestLow.toString())))
                .andExpect(jsonPath("$.items[2].id", equalTo(olderHigh.toString())))
                .andExpect(jsonPath("$.items[3].id", equalTo(olderLow.toString())));
    }

    @Test
    void firstPageOverLimit_emitsNonNullNextCursor() throws Exception {
        seed(UUID.randomUUID(), T.plusSeconds(3), "u", "t", "r", Outcome.SUCCESS);
        seed(UUID.randomUUID(), T.plusSeconds(2), "u", "t", "r", Outcome.SUCCESS);
        seed(UUID.randomUUID(), T.plusSeconds(1), "u", "t", "r", Outcome.SUCCESS);

        mockMvc.perform(get("/audit-events").param("actor", "u").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.next_cursor", notNullValue()))
                .andExpect(jsonPath("$.next_cursor").value(not(equalTo(""))))
                .andExpect(jsonPath("$.next_cursor").value(matchesPattern(".+")));
    }

    @Test
    void correlationId_echoedOnSuccessfulQuery() throws Exception {
        seed(UUID.randomUUID(), T, "u_42", "doc.read", "doc:1", Outcome.SUCCESS);
        String supplied = "2f3c4d5e-6789-4abc-9def-0123456789ab";

        mockMvc.perform(get("/audit-events").param("actor", "u_42").header("X-Correlation-Id", supplied))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", supplied));
    }

    @Test
    void correlationId_generatedWhenAbsentOnSuccessfulQuery() throws Exception {
        seed(UUID.randomUUID(), T, "u_42", "doc.read", "doc:1", Outcome.SUCCESS);

        mockMvc.perform(get("/audit-events").param("actor", "u_42"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "X-Correlation-Id",
                        matchesPattern("^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$")));
    }

    @Test
    void eventType_bindsFromSnakeCaseRequestParam() throws Exception {
        UUID match = UUID.randomUUID();
        seed(match, T, "u", "doc.read", "r", Outcome.SUCCESS);
        seed(UUID.randomUUID(), T, "u", "doc.write", "r", Outcome.SUCCESS);

        mockMvc.perform(get("/audit-events").param("actor", "u").param("event_type", "doc.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].event_type", equalTo("doc.read")))
                .andExpect(jsonPath("$.items[0].id", equalTo(match.toString())));

        mockMvc.perform(get("/audit-events").param("actor", "u").param("eventType", "doc.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThan(1))));
    }

    private void seed(UUID id, OffsetDateTime recordedAt, String actor, String eventType, String resource, Outcome outcome) {
        em.createNativeQuery(
                        "INSERT INTO audit_events (id, recorded_at, actor, event_type, resource, outcome, context) "
                                + "VALUES (?1, ?2, ?3, ?4, ?5, CAST(?6 AS audit_outcome), CAST(?7 AS jsonb))")
                .setParameter(1, id)
                .setParameter(2, recordedAt)
                .setParameter(3, actor)
                .setParameter(4, eventType)
                .setParameter(5, resource)
                .setParameter(6, outcome.name())
                .setParameter(7, "{}")
                .executeUpdate();
        em.flush();
        em.clear();
    }
}
