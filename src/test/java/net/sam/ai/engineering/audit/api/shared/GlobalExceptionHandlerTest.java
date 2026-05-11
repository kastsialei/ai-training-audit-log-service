package net.sam.ai.engineering.audit.api.shared;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.sam.ai.engineering.audit.api.ingestion.IngestionController;
import net.sam.ai.engineering.audit.application.ingestion.IngestEventCommand;
import net.sam.ai.engineering.audit.application.ingestion.IngestEventUseCase;
import net.sam.ai.engineering.audit.domain.ingestion.InvalidAuditEventException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@WebMvcTest(controllers = {IngestionController.class, GlobalExceptionHandlerTest.BoomController.class})
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.BoomController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    IngestEventUseCase useCase;

    @Test
    void mapsInvalidAuditEventExceptionTo400ProblemJson() throws Exception {
        when(useCase.execute(any(IngestEventCommand.class)))
                .thenThrow(new InvalidAuditEventException("context must be a JSON object (use {} for empty)"));

        String body =
                """
                {
                  "actor": "alice",
                  "event_type": "user.login",
                  "resource": "user:42",
                  "context": {}
                }
                """;

        mockMvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", startsWith("https://audit-log-service/problems/")))
                .andExpect(jsonPath("$.type", endsWith("/invalid-audit-event")))
                .andExpect(jsonPath("$.title", not(equalTo(null))))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.detail", not(equalTo(null))))
                .andExpect(jsonPath("$.instance", equalTo("/audit-events")));
    }

    @Test
    void catchAllReturns500ProblemJsonWithoutLeakingInternals() throws Exception {
        mockMvc.perform(get("/test-only/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", endsWith("/internal-error")))
                .andExpect(jsonPath("$.status", equalTo(500)))
                .andExpect(jsonPath("$.detail", not(containsString("BOOM secret stack message"))))
                .andExpect(jsonPath("$.detail", not(containsString("IllegalStateException"))))
                .andExpect(jsonPath("$.instance", equalTo("/test-only/boom")));
    }

    /** Test-only controller used to exercise the catch-all handler. */
    @Controller
    static class BoomController {
        @GetMapping("/test-only/boom")
        @ResponseBody
        public String boom() {
            throw new IllegalStateException("BOOM secret stack message");
        }
    }
}
