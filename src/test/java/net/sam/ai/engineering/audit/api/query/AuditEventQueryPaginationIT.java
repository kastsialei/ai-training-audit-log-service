package net.sam.ai.engineering.audit.api.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pagination invariants per design.md §5: canonical sort, page stability under
 * strictly-newer inserts, and {@code from == to} half-open empty window. Not
 * {@code @Transactional}: cursor walks span HTTP calls and need committed rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuditEventQueryPaginationIT {

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

    private static final OffsetDateTime T = OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        fixtures = new QueryFixtures(dataSource);
    }

    @Test
    void walkPagesWithLimit2_returnsAllRowsExactlyOnce_lastPageHasNullCursor() throws Exception {
        String actor = "walk_actor";
        List<QueryFixtures.SeedRow> rows = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            rows.add(new QueryFixtures.SeedRow(
                    UUID.randomUUID(), T.plusSeconds(i), actor, "doc.read", "doc:" + i, Outcome.SUCCESS, "{}"));
        }
        fixtures.seed(rows);

        List<String> walked = new ArrayList<>();
        List<Integer> pageSizes = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode body = fetchPage(actor, 2, cursor);
            pageSizes.add(body.get("items").size());
            for (JsonNode item : body.get("items")) {
                walked.add(item.get("id").asText());
            }
            cursor = body.get("next_cursor").isNull() ? null : body.get("next_cursor").asText();
            assertThat(pageSizes.size()).as("walk overran 4 pages").isLessThanOrEqualTo(4);
        } while (cursor != null);

        assertThat(pageSizes).containsExactly(2, 2, 2, 1);
        assertThat(new HashSet<>(walked)).hasSameSizeAs(walked);

        List<String> expected = rows.stream()
                .sorted(Comparator.comparing(QueryFixtures.SeedRow::recordedAt)
                        .reversed()
                        .thenComparing(r -> r.id().toString(), Comparator.reverseOrder()))
                .map(r -> r.id().toString())
                .toList();
        assertThat(walked).containsExactlyElementsOf(expected);
    }

    @Test
    void newerInsertDuringWalk_doesNotAppearInRemainingPages() throws Exception {
        // design.md §5 invariant #2: strictly-newer inserts during a walk must
        // not surface. Same-instant inserts are covered by id DESC tiebreak in
        // T-8 reader tests and the property test.
        String actor = "concurrent_actor";
        List<QueryFixtures.SeedRow> seed = new ArrayList<>();
        List<String> originalIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            originalIds.add(id.toString());
            seed.add(new QueryFixtures.SeedRow(id, T.plusSeconds(i), actor, "doc.read", "r", Outcome.SUCCESS, "{}"));
        }
        fixtures.seed(seed);

        JsonNode page1 = fetchPage(actor, 2, null);
        String cursor = page1.get("next_cursor").asText();
        List<String> walked = new ArrayList<>();
        for (JsonNode item : page1.get("items")) {
            walked.add(item.get("id").asText());
        }

        UUID injected = UUID.randomUUID();
        fixtures.seed(List.of(new QueryFixtures.SeedRow(
                injected, T.plusSeconds(100), actor, "doc.read", "r", Outcome.SUCCESS, "{}")));

        while (cursor != null) {
            JsonNode page = fetchPage(actor, 2, cursor);
            for (JsonNode item : page.get("items")) {
                walked.add(item.get("id").asText());
            }
            cursor = page.get("next_cursor").isNull() ? null : page.get("next_cursor").asText();
        }

        assertThat(walked).doesNotContain(injected.toString());
        List<String> expectedByTime = new ArrayList<>(originalIds);
        Collections.reverse(expectedByTime);
        assertThat(walked).containsExactlyElementsOf(expectedByTime);
    }

    @Test
    void fromEqualsTo_returnsEmptyHalfOpenWindow() throws Exception {
        String actor = "empty_window_actor";
        fixtures.seed(List.of(
                new QueryFixtures.SeedRow(UUID.randomUUID(), T, actor, "doc.read", "r", Outcome.SUCCESS, "{}"),
                new QueryFixtures.SeedRow(
                        UUID.randomUUID(), T.plusSeconds(5), actor, "doc.read", "r", Outcome.SUCCESS, "{}")));

        String t = T.toString();
        MvcResult result = mockMvc.perform(get("/audit-events").param("actor", actor).param("from", t).param("to", t))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("items").size()).isZero();
        assertThat(body.get("next_cursor").isNull()).isTrue();
    }

    private JsonNode fetchPage(String actor, int limit, String cursor) throws Exception {
        var req = get("/audit-events").param("actor", actor).param("limit", Integer.toString(limit));
        if (cursor != null) {
            req = req.param("cursor", cursor);
        }
        MvcResult result = mockMvc.perform(req).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
