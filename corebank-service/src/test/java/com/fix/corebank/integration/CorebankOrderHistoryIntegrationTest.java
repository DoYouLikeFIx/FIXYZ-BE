package com.fix.corebank.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
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
    "spring.datasource.url=jdbc:h2:mem:core_order_history_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "internal.secret=test-secret"
})
class CorebankOrderHistoryIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearOrders() {
    jdbcTemplate.update("DELETE FROM orders");
  }

  @Test
  void shouldReturnPaginatedHistoryOrderedByCreatedAtDesc() throws Exception {
    insertOrder(
        1L,
        "123e4567-e89b-42d3-a456-426614174301",
        "005930",
        "BUY",
        new BigDecimal("3.0000"),
        new BigDecimal("70100.0000"),
        "FILLED",
        Instant.parse("2026-03-01T10:00:00Z")
    );
    insertOrder(
        1L,
        "123e4567-e89b-42d3-a456-426614174302",
        "000660",
        "SELL",
        new BigDecimal("2.0000"),
        new BigDecimal("120000.0000"),
        "CANCELED",
        Instant.parse("2026-03-01T12:00:00Z")
    );
    insertOrder(
        1L,
        "123e4567-e89b-42d3-a456-426614174303",
        "005935",
        "BUY",
        new BigDecimal("1.0000"),
        new BigDecimal("50000.0000"),
        "FAILED",
        Instant.parse("2026-03-01T11:00:00Z")
    );

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/orders", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-history-page")
            .param("memberId", "1")
            .param("page", "0")
            .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-history-page"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.content[0].clOrdId").value("123e4567-e89b-42d3-a456-426614174302"))
        .andExpect(jsonPath("$.data.content[0].symbol").value("000660"))
        .andExpect(jsonPath("$.data.content[0].symbolName").value("SK하이닉스"))
        .andExpect(jsonPath("$.data.content[0].qty").value(2.0))
        .andExpect(jsonPath("$.data.content[0].unitPrice").value(120000.0))
        .andExpect(jsonPath("$.data.content[0].totalAmount").value(240000.0))
        .andExpect(jsonPath("$.data.content[1].clOrdId").value("123e4567-e89b-42d3-a456-426614174303"))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.number").value(0))
        .andExpect(jsonPath("$.data.size").value(2));
  }

  @Test
  void shouldReturnDeterministicEmptyHistoryContract() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/orders", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "1")
            .param("page", "0")
            .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content.length()").value(0))
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.totalPages").value(0))
        .andExpect(jsonPath("$.data.number").value(0))
        .andExpect(jsonPath("$.data.size").value(20));
  }

  @Test
  void shouldReturnForbiddenWhenOwnershipMismatches() throws Exception {
    insertOrder(
        1L,
        "123e4567-e89b-42d3-a456-426614174304",
        "005930",
        "BUY",
        new BigDecimal("1.0000"),
        new BigDecimal("70000.0000"),
        "FILLED",
        Instant.parse("2026-03-01T09:00:00Z")
    );

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/orders", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-history-forbidden")
            .param("memberId", "2")
            .param("page", "0")
            .param("size", "20"))
        .andExpect(status().isForbidden())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-history-forbidden"))
        .andExpect(jsonPath("$.code").value("AUTH-005"))
        .andExpect(jsonPath("$.message").value("forbidden account ownership"))
        .andExpect(jsonPath("$.path").value("/internal/v1/accounts/1/orders"));
  }

  @Test
  void shouldReturnBadRequestForMalformedPagination() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/orders", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "1")
            .param("page", "-1")
            .param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  private void insertOrder(
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal qty,
      BigDecimal unitPrice,
      String status,
      Instant createdAt
  ) {
    Timestamp timestamp = Timestamp.from(createdAt);
    jdbcTemplate.update(
        """
            INSERT INTO orders (
              account_id, cl_ord_id, symbol, side, order_qty, order_price,
              status, requested_at, created_at, updated_at, version,
              external_sync_status, fep_reference_id, failure_reason
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, NULL, NULL)
            """,
        accountId,
        clOrdId,
        symbol,
        side,
        qty,
        unitPrice,
        status,
        timestamp,
        timestamp,
        timestamp
    );
  }
}
