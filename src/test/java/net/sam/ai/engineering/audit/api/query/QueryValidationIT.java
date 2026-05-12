package net.sam.ai.engineering.audit.api.query;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class QueryValidationIT {

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

    private static final String BASE = "https://audit-log-service/problems/";
    private static final String LEAK_TOKEN = "2099-12-31T23:59:59.000000001Z";

    @ParameterizedTest(name = "[{index}] {0} -> no-filter")
    @CsvSource({
        "''",
        "limit=10",
        "cursor=anything"
    })
    void noSubstantiveFilters_returnNoFilter(String query) throws Exception {
        ResultActions actions = mockMvc.perform(get(buildPath(query))).andExpect(status().isBadRequest());
        assertProblem(actions, BASE + "no-filter");
    }

    @ParameterizedTest(name = "[{index}] {0} -> blank-filter")
    @CsvSource({
        "actor=&resource=u_1",
        "resource=&actor=u_1",
        "event_type=&actor=u_1"
    })
    void blankFilters_returnBlankFilter(String query) throws Exception {
        ResultActions actions = mockMvc.perform(get(buildPath(query))).andExpect(status().isBadRequest());
        assertProblem(actions, BASE + "blank-filter");
    }

    @Test
    void whitespaceOnlyActor_returnsBlankFilter() throws Exception {
        ResultActions actions = mockMvc.perform(get("/audit-events").param("actor", "   ").param("resource", "u_1"))
                .andExpect(status().isBadRequest());
        assertProblem(actions, BASE + "blank-filter");
    }

    @Test
    void invalidOutcome_returnsInvalidOutcome() throws Exception {
        ResultActions actions = mockMvc.perform(get("/audit-events?outcome=BOGUS&actor=u_1"))
                .andExpect(status().isBadRequest());
        assertProblem(actions, BASE + "invalid-outcome");
    }

    @ParameterizedTest(name = "[{index}] timestamp {0} -> invalid-timestamp")
    @CsvSource({
        "from=not-a-date&actor=u_1",
        "to=not-a-date&actor=u_1",
        "from=2026-05-01T00:00:00&actor=u_1"
    })
    void invalidTimestamp_returnsInvalidTimestamp(String query) throws Exception {
        ResultActions actions = mockMvc.perform(get(buildPath(query))).andExpect(status().isBadRequest());
        assertProblem(actions, BASE + "invalid-timestamp");
    }

    @Test
    void fromGreaterThanTo_returnsInvalidTimeRange() throws Exception {
        ResultActions actions = mockMvc.perform(
                        get("/audit-events?from=2026-05-02T00:00:00Z&to=2026-05-01T00:00:00Z&actor=u_1"))
                .andExpect(status().isBadRequest());
        assertProblem(actions, BASE + "invalid-time-range");
    }

    @ParameterizedTest(name = "[{index}] limit={0} -> invalid-limit")
    @CsvSource({"0", "201"})
    void limitOutOfRange_returnsInvalidLimit(String limit) throws Exception {
        ResultActions actions = mockMvc.perform(get("/audit-events?actor=u_1&limit=" + limit))
                .andExpect(status().isBadRequest());
        assertProblem(actions, BASE + "invalid-limit");
    }

    @Test
    void malformedCursor_returnsInvalidCursor() throws Exception {
        ResultActions actions = mockMvc.perform(get("/audit-events?actor=u_1&cursor=@@@not-base64@@@"))
                .andExpect(status().isBadRequest());
        assertProblem(actions, BASE + "invalid-cursor");
        actions.andExpect(jsonPath("$.detail", equalTo("cursor is malformed")));
    }

    @Test
    void malformedCursor_doesNotLeakRawInput() throws Exception {
        String rawCursor = "@@@SECRET-LEAK-MARKER@@@";
        MvcResult result = mockMvc.perform(get("/audit-events?actor=u_1&cursor=" + rawCursor))
                .andExpect(status().isBadRequest())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("SECRET-LEAK-MARKER"),
                "Response body must not echo raw cursor input: " + body);
    }

    @Test
    void cursorFilterMismatch_returnsCursorFilterMismatch() throws Exception {
        String cursor = encodeCursor(LEAK_TOKEN, "00000000-0000-0000-0000-000000000001", "deadbeefdeadbeef");
        ResultActions actions = mockMvc.perform(get("/audit-events?actor=u_1&cursor=" + cursor))
                .andExpect(status().isBadRequest());
        assertProblem(actions, BASE + "cursor-filter-mismatch");
        actions.andExpect(jsonPath("$.detail", equalTo("cursor was issued for a different filter set")));
    }

    @Test
    void cursorFilterMismatch_doesNotLeakDecodedFields() throws Exception {
        String cursor = encodeCursor(LEAK_TOKEN, "00000000-0000-0000-0000-000000000001", "deadbeefdeadbeef");
        MvcResult result = mockMvc.perform(get("/audit-events?actor=u_1&cursor=" + cursor))
                .andExpect(status().isBadRequest())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains(LEAK_TOKEN),
                "Response body must not echo decoded cursor fields: " + body);
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains(cursor),
                "Response body must not echo the supplied cursor: " + body);
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("deadbeefdeadbeef"),
                "Response body must not echo the supplied fp: " + body);
    }

    @Test
    void missingHeader_returnsGeneratedCorrelationId() throws Exception {
        mockMvc.perform(get("/audit-events"))
                .andExpect(status().isBadRequest())
                .andExpect(header()
                        .string(
                                "X-Correlation-Id",
                                matchesPattern("^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$")));
    }

    @Test
    void suppliedHeader_isEchoedOn400() throws Exception {
        String supplied = "2f3c4d5e-6789-4abc-9def-0123456789ab";
        mockMvc.perform(get("/audit-events").header("X-Correlation-Id", supplied))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-Id", supplied));
    }

    private static String buildPath(String query) {
        return query == null || query.isEmpty() ? "/audit-events" : "/audit-events?" + query;
    }

    private static void assertProblem(ResultActions actions, String typeUri) throws Exception {
        actions.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", equalTo(typeUri)))
                .andExpect(jsonPath("$.title", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.detail", notNullValue()))
                .andExpect(jsonPath("$.instance", endsWith("/audit-events")))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    private static String encodeCursor(String rt, String id, String fp) {
        String json = "{\"rt\":\"" + rt + "\",\"id\":\"" + id + "\",\"fp\":\"" + fp + "\"}";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }
}
