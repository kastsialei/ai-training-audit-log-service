package net.sam.ai.engineering.audit.api.ingestion;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IngestionIT {

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
    DataSource dataSource;

    @Test
    void postCreatesEventWithServerAssignedIdAndTimestamp() throws Exception {
        String body =
                """
                {
                  "actor": "alice@example.com",
                  "event_type": "user.login",
                  "resource": "user:42",
                  "outcome": "SUCCESS",
                  "context": {"ip": "10.0.0.1"}
                }
                """;

        mockMvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.recorded_at", not(equalTo(null))))
                .andExpect(jsonPath("$.actor", equalTo("alice@example.com")))
                .andExpect(jsonPath("$.event_type", equalTo("user.login")))
                .andExpect(jsonPath("$.resource", equalTo("user:42")))
                .andExpect(jsonPath("$.outcome", equalTo("SUCCESS")))
                .andExpect(jsonPath("$.context.ip", equalTo("10.0.0.1")));
    }

    @Test
    void rejectsRequestMissingRequiredFields() throws Exception {
        String body =
                """
                { "event_type": "user.login", "resource": "user:42", "context": {} }
                """;

        mockMvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", equalTo("https://audit-log-service/problems/validation-error")))
                .andExpect(jsonPath("$.title", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.detail", notNullValue()))
                .andExpect(jsonPath("$.instance", equalTo("/audit-events")));
    }

    @Test
    void rejectsJsonNullContext() throws Exception {
        String body =
                """
                {
                  "actor": "alice@example.com",
                  "event_type": "user.login",
                  "resource": "user:42",
                  "context": null
                }
                """;

        mockMvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", equalTo("https://audit-log-service/problems/validation-error")))
                .andExpect(jsonPath("$.title", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.detail", notNullValue()))
                .andExpect(jsonPath("$.instance", equalTo("/audit-events")));
    }

    @Test
    void rejectsMalformedJsonBody() throws Exception {
        String body = "{ not valid json";

        mockMvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", equalTo("https://audit-log-service/problems/malformed-request")))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.instance", equalTo("/audit-events")));
    }

    @Test
    void databaseRejectsUpdateAndDelete() throws Exception {
        String body =
                """
                {
                  "actor": "bob@example.com",
                  "event_type": "policy.updated",
                  "resource": "policy:1",
                  "context": {}
                }
                """;
        mockMvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            SQLException onUpdate = assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("UPDATE audit_events SET actor = 'hacker'"));
            assertTrue(
                    onUpdate.getMessage().contains("append-only"),
                    "expected append-only error, got: " + onUpdate.getMessage());

            SQLException onDelete = assertThrows(
                    SQLException.class, () -> statement.executeUpdate("DELETE FROM audit_events"));
            assertTrue(
                    onDelete.getMessage().contains("append-only"),
                    "expected append-only error, got: " + onDelete.getMessage());
        }
    }
}
