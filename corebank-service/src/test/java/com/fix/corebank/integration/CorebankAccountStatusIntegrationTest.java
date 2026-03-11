package com.fix.corebank.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import java.math.BigDecimal;
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
    "spring.datasource.url=jdbc:h2:mem:core_account_status_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "internal.secret=test-secret"
})
class CorebankAccountStatusIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetFixtureAccounts() {
    jdbcTemplate.update("DELETE FROM accounts WHERE id <> 1");
    jdbcTemplate.update("DELETE FROM member WHERE id <> 1");
  }

  @Test
  void shouldReturnOwnedActiveAccountStatusContract() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/status", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-status-active")
            .param("memberId", "1"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-status-active"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.memberId").value(1L))
        .andExpect(jsonPath("$.data.accountNumber").value("110123456789"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.orderEligible").value(true))
        .andExpect(jsonPath("$.data.denialCode").doesNotExist())
        .andExpect(jsonPath("$.data.asOf").isNotEmpty());
  }

  @Test
  void shouldReturnOrd012WhenAccountIsFrozen() throws Exception {
    insertMember(2L, "M-2002", "status-frozen@fix.local");
    insertAccount(2L, "220123456789", 2L, "FROZEN");

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/status", 2L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("FROZEN"))
        .andExpect(jsonPath("$.data.orderEligible").value(false))
        .andExpect(jsonPath("$.data.denialCode").value("ORD-012"));
  }

  @Test
  void shouldReturnOrd012WhenAccountIsClosed() throws Exception {
    insertMember(3L, "M-3003", "status-closed@fix.local");
    insertAccount(3L, "330123456789", 3L, "CLOSED");

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/status", 3L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CLOSED"))
        .andExpect(jsonPath("$.data.orderEligible").value(false))
        .andExpect(jsonPath("$.data.denialCode").value("ORD-012"));
  }

  @Test
  void shouldReturnForbiddenWhenOwnershipMismatches() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/status", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-status-forbidden")
            .param("memberId", "2"))
        .andExpect(status().isForbidden())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-status-forbidden"))
        .andExpect(jsonPath("$.code").value("AUTH-005"))
        .andExpect(jsonPath("$.message").value("forbidden account ownership"))
        .andExpect(jsonPath("$.path").value("/internal/v1/accounts/1/status"));
  }

  @Test
  void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/status", 999L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CORE_001"))
        .andExpect(jsonPath("$.message").value("account not found"))
        .andExpect(jsonPath("$.path").value("/internal/v1/accounts/999/status"));
  }

  private void insertMember(Long memberId, String memberNo, String email) {
    jdbcTemplate.update(
        "INSERT INTO member (id, member_no, email) VALUES (?, ?, ?)",
        memberId,
        memberNo,
        email
    );
  }

  private void insertAccount(Long accountId, String accountNo, Long memberId, String status) {
    jdbcTemplate.update(
        """
            INSERT INTO accounts (
              id, account_no, member_id, status, currency, cash_balance, daily_sell_limit,
              created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """,
        accountId,
        accountNo,
        memberId,
        status,
        "KRW",
        new BigDecimal("1000000.0000"),
        new BigDecimal("500.0000")
    );
  }
}
