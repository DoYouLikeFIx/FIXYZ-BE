package com.fix.corebank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_account_status_transition_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "internal.secret=test-secret"
})
class CorebankAccountStatusTransitionIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetFixtures() {
    jdbcTemplate.update("DELETE FROM account_status_events");
    jdbcTemplate.update("DELETE FROM accounts WHERE id <> 1");
    jdbcTemplate.update("DELETE FROM member WHERE id <> 1");
    jdbcTemplate.update("UPDATE accounts SET status = 'ACTIVE' WHERE id = 1");
  }

  @Test
  void shouldTransitionStatusAndEmitSingleEvent() throws Exception {
    mockMvc.perform(patch("/internal/v1/accounts/{accountId}/status", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-status-transition")
            .contentType("application/json")
            .content(statusTransitionBody(1L, "FROZEN", "risk-control", "ops-admin", "ticket=FIX-43")))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-status-transition"))
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.memberId").value(1L))
        .andExpect(jsonPath("$.data.previousStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data.newStatus").value("FROZEN"))
        .andExpect(jsonPath("$.data.changed").value(true))
        .andExpect(jsonPath("$.data.eventId").isNumber())
        .andExpect(jsonPath("$.data.reason").value("risk-control"))
        .andExpect(jsonPath("$.data.actor").value("ops-admin"))
        .andExpect(jsonPath("$.data.context").value("ticket=FIX-43"));

    String accountStatus = jdbcTemplate.queryForObject("SELECT status FROM accounts WHERE id = 1", String.class);
    assertThat(accountStatus).isEqualTo("FROZEN");

    Integer eventCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM account_status_events WHERE account_id = 1",
        Integer.class
    );
    assertThat(eventCount).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT previous_status FROM account_status_events WHERE account_id = 1",
        String.class
    )).isEqualTo("ACTIVE");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT new_status FROM account_status_events WHERE account_id = 1",
        String.class
    )).isEqualTo("FROZEN");
  }

  @Test
  void shouldNotEmitEventWhenStatusIsUnchanged() throws Exception {
    mockMvc.perform(patch("/internal/v1/accounts/{accountId}/status", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-status-noop")
            .contentType("application/json")
            .content(statusTransitionBody(1L, "ACTIVE", "manual-check", "ops-admin", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.changed").value(false))
        .andExpect(jsonPath("$.data.eventId").doesNotExist())
        .andExpect(jsonPath("$.data.previousStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data.newStatus").value("ACTIVE"));

    Integer eventCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM account_status_events WHERE account_id = 1",
        Integer.class
    );
    assertThat(eventCount).isEqualTo(0);
  }

  @Test
  void shouldReturnForbiddenWhenOwnershipMismatches() throws Exception {
    mockMvc.perform(patch("/internal/v1/accounts/{accountId}/status", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-status-forbidden")
            .contentType("application/json")
            .content(statusTransitionBody(2L, "FROZEN", "risk-control", "ops-admin", null)))
        .andExpect(status().isForbidden())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-status-forbidden"))
        .andExpect(jsonPath("$.code").value("AUTH-005"))
        .andExpect(jsonPath("$.message").value("forbidden account ownership"));
  }

  @Test
  void shouldReturnBadRequestWhenTransitionStatusIsInvalid() throws Exception {
    mockMvc.perform(patch("/internal/v1/accounts/{accountId}/status", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-status-invalid")
            .contentType("application/json")
            .content(statusTransitionBody(1L, "PAUSED", "risk-control", "ops-admin", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  private String statusTransitionBody(
      Long memberId,
      String status,
      String reason,
      String actor,
      String context
  ) {
    String contextField = context == null ? "" : ",\n\"context\":\"" + context + "\"";
    return """
        {
          "memberId": %d,
          "status": "%s",
          "reason": "%s",
          "actor": "%s"%s
        }
        """.formatted(memberId, status, reason, actor, contextField);
  }
}
