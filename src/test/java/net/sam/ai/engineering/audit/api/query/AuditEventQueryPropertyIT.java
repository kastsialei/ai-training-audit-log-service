package net.sam.ai.engineering.audit.api.query;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.sql.DataSource;
import net.sam.ai.engineering.audit.domain.shared.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Randomized invariant test: a paginated cursor walk returns the same ordered ID
 * sequence as a single un-paginated query of the same filter set (design.md §5
 * invariant #3). Deterministic via {@link #DEFAULT_SEED}; override with
 * {@code -Dquery.property.seed=<long>} to reproduce. Not {@code @Transactional}
 * because the walk spans HTTP calls and needs committed rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuditEventQueryPropertyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper objectMapper;

    private QueryFixtures fixtures;

    static final long DEFAULT_SEED = 0xCAFEBABEL;
    private static final long ROOT_SEED = Long.getLong("query.property.seed", DEFAULT_SEED);
    private static final OffsetDateTime T = OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    private static final String[] EVENT_TYPES = {"doc.read", "doc.write", "doc.delete"};
    private static final String[] RESOURCES = {"doc:alpha", "doc:beta", "doc:gamma"};
    private static final Outcome[] OUTCOMES = {Outcome.SUCCESS, Outcome.DENIED, Outcome.ERROR, null};

    @BeforeEach
    void setUp() {
        fixtures = new QueryFixtures(dataSource);
    }

    @Test
    void cursorWalkMatchesSingleQueryOrder_over100RandomTrials() throws Exception {
        for (int t = 0; t < 100; t++) {
            long trialSeed = ROOT_SEED ^ t;
            Random rng = new Random(trialSeed);
            String actor = "prop_actor_" + t;
            int n = 5 + rng.nextInt(21);
            List<QueryFixtures.SeedRow> rows = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                rows.add(new QueryFixtures.SeedRow(
                        // Deterministic UUIDs so reruns reproduce tie-break ordering.
                        new UUID(rng.nextLong(), rng.nextLong()),
                        T.plusSeconds(rng.nextInt(10001)),
                        actor,
                        EVENT_TYPES[rng.nextInt(EVENT_TYPES.length)],
                        RESOURCES[rng.nextInt(RESOURCES.length)],
                        OUTCOMES[rng.nextInt(OUTCOMES.length)],
                        "{}"));
            }
            fixtures.seed(rows);

            String pickEventType = null;
            String pickResource = null;
            Outcome pickOutcome = null;
            switch (rng.nextInt(4)) {
                case 1 -> pickEventType = EVENT_TYPES[rng.nextInt(EVENT_TYPES.length)];
                case 2 -> pickResource = RESOURCES[rng.nextInt(RESOURCES.length)];
                case 3 -> {
                    Outcome c = OUTCOMES[rng.nextInt(OUTCOMES.length)];
                    if (c != null) pickOutcome = c;
                }
                default -> {}
            }
            int limit = 1 + rng.nextInt(5);

            List<String> oracle = collectIds(fetchPage(actor, pickEventType, pickResource, pickOutcome, 200, null));
            List<String> walked = new ArrayList<>();
            String cursor = null;
            do {
                JsonNode page = fetchPage(actor, pickEventType, pickResource, pickOutcome, limit, cursor);
                walked.addAll(collectIds(page));
                cursor = page.get("next_cursor").isNull() ? null : page.get("next_cursor").asText();
            } while (cursor != null);

            if (!walked.equals(oracle)) {
                org.junit.jupiter.api.Assertions.fail(String.format(
                        "Property invariant violated.%n  trial=%d trialSeed=0x%x rootSeed=0x%x"
                                + " (rerun: -Dquery.property.seed=%d)%n"
                                + "  filters: actor=%s eventType=%s resource=%s outcome=%s limit=%d%n"
                                + "  walked=%s%n  oracle=%s%n  firstDivergenceIndex=%d",
                        t, trialSeed, ROOT_SEED, trialSeed,
                        actor, pickEventType, pickResource, pickOutcome, limit,
                        walked, oracle, firstDivergence(walked, oracle)));
            }
        }
    }

    private static List<String> collectIds(JsonNode body) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : body.get("items")) {
            ids.add(item.get("id").asText());
        }
        return ids;
    }

    private JsonNode fetchPage(
            String actor, String eventType, String resource, Outcome outcome, int limit, String cursor)
            throws Exception {
        MockHttpServletRequestBuilder req =
                get("/audit-events").param("actor", actor).param("limit", Integer.toString(limit));
        if (eventType != null) req = req.param("event_type", eventType);
        if (resource != null) req = req.param("resource", resource);
        if (outcome != null) req = req.param("outcome", outcome.name());
        if (cursor != null) req = req.param("cursor", cursor);
        MvcResult result = mockMvc.perform(req).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static int firstDivergence(List<String> a, List<String> b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            if (!a.get(i).equals(b.get(i))) return i;
        }
        return n;
    }
}
