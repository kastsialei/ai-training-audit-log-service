package net.sam.ai.engineering.audit.api.shared;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.sam.ai.engineering.audit.domain.ingestion.InvalidAuditEventException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class GlobalExceptionHandlerIT {

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

    @Test
    void invalidAuditEventException_isMappedToBadRequestProblemJson() throws Exception {
        mockMvc.perform(get("/__test/throw/invalid-audit-event"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", equalTo("https://audit-log-service/problems/invalid-audit-event")))
                .andExpect(jsonPath("$.title", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.detail", notNullValue()))
                .andExpect(jsonPath("$.instance", equalTo("/__test/throw/invalid-audit-event")));
    }

    @Test
    void unhandledException_isMappedToInternalErrorWithoutLeakingInternals() throws Exception {
        mockMvc.perform(get("/__test/throw/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", equalTo("https://audit-log-service/problems/internal-error")))
                .andExpect(jsonPath("$.status", equalTo(500)))
                .andExpect(jsonPath("$.title", notNullValue()))
                .andExpect(jsonPath("$.detail", notNullValue()))
                .andExpect(jsonPath("$.instance", equalTo("/__test/throw/runtime")))
                .andExpect(content().string(not(containsString("SecretInternalDetail"))))
                .andExpect(content().string(not(containsString("RuntimeException"))));
    }

    @Test
    void missingRequiredQueryParam_isMappedToBadRequestProblemJson() throws Exception {
        mockMvc.perform(get("/__test/require-param"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", equalTo("https://audit-log-service/problems/malformed-request")))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.instance", equalTo("/__test/require-param")));
    }

    @Test
    void typeMismatchOnQueryParam_isMappedToBadRequestProblemJson() throws Exception {
        mockMvc.perform(get("/__test/typed-param").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", equalTo("https://audit-log-service/problems/validation-error")))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.instance", equalTo("/__test/typed-param")));
    }

    @TestConfiguration
    static class ThrowingControllerConfig {

        @RestController
        static class ThrowingController {

            @GetMapping("/__test/throw/invalid-audit-event")
            public void throwInvalidAuditEvent() {
                throw new InvalidAuditEventException("actor must not be blank");
            }

            @GetMapping("/__test/throw/runtime")
            public void throwRuntime() {
                throw new RuntimeException("SecretInternalDetail-do-not-leak");
            }

            @GetMapping("/__test/require-param")
            public String requireParam(@RequestParam("name") String name) {
                return name;
            }

            @GetMapping("/__test/typed-param")
            public String typedParam(@RequestParam("value") int value) {
                return String.valueOf(value);
            }
        }
    }
}
