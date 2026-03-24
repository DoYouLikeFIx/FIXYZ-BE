package com.fix.corebank.integration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.web.CommonHeaders;
import com.fix.corebank.client.FepQuoteSnapshotClient;
import com.fix.corebank.client.FepQuoteSnapshotResult;
import com.fix.corebank.service.CorebankOrderService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CorebankAccountPositionIntegrationTest.FixedClockConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_account_position_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "corebank.market-data.max-quote-age-ms=5000",
    "internal.secret=test-secret"
})
class CorebankAccountPositionIntegrationTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-03-20T00:00:06Z");
  private static final Instant FIXED_FRESH_QUOTE_AS_OF = FIXED_NOW.minusSeconds(1);
  private static final Instant FIXED_STALE_QUOTE_AS_OF = FIXED_NOW.minusMillis(6_000L);

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private CorebankOrderService corebankOrderService;

  @MockBean
  private FepQuoteSnapshotClient fepQuoteSnapshotClient;

  @BeforeEach
  void setUpQuoteSnapshots() {
    ReflectionTestUtils.setField(corebankOrderService, "limitWindowClock", Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    resetExecutionHistory();
    reset(fepQuoteSnapshotClient);
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshot(eq("005930"), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenReturn(samsungSnapshot());
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshot(eq("000660"), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenReturn(hynixSnapshot());
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshots(anyList(), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenReturn(Map.of(
            "005930", samsungSnapshot(),
            "000660", hynixSnapshot()
        ));
  }

  private void resetExecutionHistory() {
    jdbcTemplate.update("DELETE FROM executions");
    jdbcTemplate.update(
        """
        INSERT INTO executions (
          id, order_id, account_id, cl_ord_id, symbol, side, exec_qty, exec_price, executed_at, created_at, updated_at, version
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        1L,
        0L,
        1L,
        "CL-SEED-BUY-001",
        "005930",
        "BUY",
        new java.math.BigDecimal("130.0000"),
        new java.math.BigDecimal("70000.0000"),
        Timestamp.from(Instant.parse("2026-03-19T23:50:00Z")),
        Timestamp.from(Instant.parse("2026-03-19T23:50:00Z")),
        Timestamp.from(Instant.parse("2026-03-19T23:50:00Z")),
        0
    );
    jdbcTemplate.update(
        """
        INSERT INTO executions (
          id, order_id, account_id, cl_ord_id, symbol, side, exec_qty, exec_price, executed_at, created_at, updated_at, version
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        2L,
        0L,
        1L,
        "CL-SEED-SELL-001",
        "005930",
        "SELL",
        new java.math.BigDecimal("10.0000"),
        new java.math.BigDecimal("70500.0000"),
        Timestamp.from(Instant.parse("2026-03-20T00:00:01Z")),
        Timestamp.from(Instant.parse("2026-03-20T00:00:01Z")),
        Timestamp.from(Instant.parse("2026-03-20T00:00:01Z")),
        0
    );
  }

  @Test
  void shouldReturnOwnedAccountPositionAndBalanceAliases() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-position-owned")
            .param("memberId", "1")
            .param("symbol", "005930"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-position-owned"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.memberId").value(1L))
        .andExpect(jsonPath("$.data.symbol").value("005930"))
        .andExpect(jsonPath("$.data.quantity").value(120.0))
        .andExpect(jsonPath("$.data.availableQuantity").value(120.0))
        .andExpect(jsonPath("$.data.availableQty").value(120.0))
        .andExpect(jsonPath("$.data.balance").value(100000000.0))
        .andExpect(jsonPath("$.data.availableBalance").value(100000000.0))
        .andExpect(jsonPath("$.data.currency").value("KRW"))
        .andExpect(jsonPath("$.data.asOf").isNotEmpty())
        .andExpect(jsonPath("$.data.avgPrice").value(70000.0))
        .andExpect(jsonPath("$.data.marketPrice").value(72050.0))
        .andExpect(jsonPath("$.data.quoteSnapshotId").value("qsnap-005930-live-001"))
        .andExpect(jsonPath("$.data.quoteAsOf").isNotEmpty())
        .andExpect(jsonPath("$.data.quoteSourceMode").value("LIVE"))
        .andExpect(jsonPath("$.data.unrealizedPnl").value(246000.0))
        .andExpect(jsonPath("$.data.realizedPnlDaily").value(5000.0))
        .andExpect(jsonPath("$.data.valuationStatus").value("FRESH"))
        .andExpect(jsonPath("$.data.valuationUnavailableReason").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void shouldReturnOwnedAccountPositionsList() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions/list", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-positions-owned")
            .param("memberId", "1"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-positions-owned"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].accountId").value(1L))
        .andExpect(jsonPath("$.data[0].memberId").value(1L))
        .andExpect(jsonPath("$.data[0].symbol").value("005930"))
        .andExpect(jsonPath("$.data[0].quantity").value(120.0))
        .andExpect(jsonPath("$.data[0].availableQuantity").value(120.0))
        .andExpect(jsonPath("$.data[0].balance").value(100000000.0))
        .andExpect(jsonPath("$.data[0].currency").value("KRW"))
        .andExpect(jsonPath("$.data[0].avgPrice").value(70000.0))
        .andExpect(jsonPath("$.data[0].marketPrice").value(72050.0))
        .andExpect(jsonPath("$.data[0].quoteSnapshotId").value("qsnap-005930-live-001"))
        .andExpect(jsonPath("$.data[0].quoteSourceMode").value("LIVE"))
        .andExpect(jsonPath("$.data[0].unrealizedPnl").value(246000.0))
        .andExpect(jsonPath("$.data[0].realizedPnlDaily").value(5000.0))
        .andExpect(jsonPath("$.data[0].valuationStatus").value("FRESH"))
        .andExpect(jsonPath("$.data[0].valuationUnavailableReason").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void shouldReturnAccountSummaryForCashOnlyFallback() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/summary", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-summary-owned")
            .param("memberId", "1"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-summary-owned"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.memberId").value(1L))
        .andExpect(jsonPath("$.data.symbol").value(""))
        .andExpect(jsonPath("$.data.quantity").value(0.0))
        .andExpect(jsonPath("$.data.availableQuantity").value(0.0))
        .andExpect(jsonPath("$.data.availableQty").value(0.0))
        .andExpect(jsonPath("$.data.balance").value(100000000.0))
        .andExpect(jsonPath("$.data.availableBalance").value(100000000.0))
        .andExpect(jsonPath("$.data.currency").value("KRW"))
        .andExpect(jsonPath("$.data.asOf").isNotEmpty())
        .andExpect(jsonPath("$.data.avgPrice").doesNotExist())
        .andExpect(jsonPath("$.data.marketPrice").doesNotExist())
        .andExpect(jsonPath("$.data.quoteSnapshotId").doesNotExist())
        .andExpect(jsonPath("$.data.quoteAsOf").doesNotExist())
        .andExpect(jsonPath("$.data.quoteSourceMode").doesNotExist())
        .andExpect(jsonPath("$.data.unrealizedPnl").doesNotExist())
        .andExpect(jsonPath("$.data.realizedPnlDaily").doesNotExist())
        .andExpect(jsonPath("$.data.valuationStatus").doesNotExist())
        .andExpect(jsonPath("$.data.valuationUnavailableReason").doesNotExist());
  }

  @Test
  void shouldReturnZeroQuantityWhenPositionDoesNotExist() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "1")
            .param("symbol", "000660"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quantity").value(0.0))
        .andExpect(jsonPath("$.data.availableQuantity").value(0.0))
        .andExpect(jsonPath("$.data.availableQty").value(0.0))
        .andExpect(jsonPath("$.data.balance").value(100000000.0))
        .andExpect(jsonPath("$.data.availableBalance").value(100000000.0))
        .andExpect(jsonPath("$.data.asOf").isNotEmpty())
        .andExpect(jsonPath("$.data.avgPrice").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.marketPrice").value(120250.0))
        .andExpect(jsonPath("$.data.quoteSnapshotId").value("qsnap-000660-live-001"))
        .andExpect(jsonPath("$.data.quoteSourceMode").value("LIVE"))
        .andExpect(jsonPath("$.data.unrealizedPnl").value(0.0))
        .andExpect(jsonPath("$.data.realizedPnlDaily").value(0.0))
        .andExpect(jsonPath("$.data.valuationStatus").value("FRESH"))
        .andExpect(jsonPath("$.data.valuationUnavailableReason").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void shouldReturnStaleQuoteEnvelopeWhenSnapshotIsTooOld() throws Exception {
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshot(eq("005930"), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenReturn(quoteSnapshot(
            "qsnap-005930-stale-001",
            "005930",
            FIXED_STALE_QUOTE_AS_OF,
            72000L,
            72100L,
            72050L
        ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-position-stale")
            .param("memberId", "1")
            .param("symbol", "005930"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-position-stale"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.symbol").value("005930"))
        .andExpect(jsonPath("$.data.avgPrice").value(70000.0))
        .andExpect(jsonPath("$.data.marketPrice").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.quoteSnapshotId").value("qsnap-005930-stale-001"))
        .andExpect(jsonPath("$.data.quoteSourceMode").value("LIVE"))
        .andExpect(jsonPath("$.data.unrealizedPnl").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.realizedPnlDaily").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.valuationStatus").value("STALE"))
        .andExpect(jsonPath("$.data.valuationUnavailableReason").value("STALE_QUOTE"));
  }

  @Test
  void shouldReturnMixedStaleAndMissingValuationStatesForOwnedAccountPositionsList() throws Exception {
    insertPosition("000660", "40.0000", "120000.0000", "2026-03-20T00:00:02Z");
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshots(anyList(), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenReturn(Map.of(
            "000660",
            quoteSnapshot(
                "qsnap-000660-stale-001",
                "000660",
                FIXED_STALE_QUOTE_AS_OF,
                120000L,
                120500L,
                120250L
            )
        ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions/list", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-positions-mixed-degraded")
            .param("memberId", "1"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-positions-mixed-degraded"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].symbol").value("000660"))
        .andExpect(jsonPath("$.data[0].avgPrice").value(120000.0))
        .andExpect(jsonPath("$.data[0].marketPrice").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[0].quoteSnapshotId").value("qsnap-000660-stale-001"))
        .andExpect(jsonPath("$.data[0].quoteAsOf").isNotEmpty())
        .andExpect(jsonPath("$.data[0].quoteSourceMode").value("LIVE"))
        .andExpect(jsonPath("$.data[0].unrealizedPnl").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[0].realizedPnlDaily").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[0].valuationStatus").value("STALE"))
        .andExpect(jsonPath("$.data[0].valuationUnavailableReason").value("STALE_QUOTE"))
        .andExpect(jsonPath("$.data[1].symbol").value("005930"))
        .andExpect(jsonPath("$.data[1].avgPrice").value(70000.0))
        .andExpect(jsonPath("$.data[1].marketPrice").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[1].quoteSnapshotId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[1].quoteAsOf").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[1].quoteSourceMode").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[1].unrealizedPnl").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[1].realizedPnlDaily").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[1].valuationStatus").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.data[1].valuationUnavailableReason").value("QUOTE_MISSING"));
  }

  @Test
  void shouldReturnUnavailableValuationWhenQuoteSnapshotIsMissing() throws Exception {
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshot(eq("005930"), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "quote snapshot not found"));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-position-missing")
            .param("memberId", "1")
            .param("symbol", "005930"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-position-missing"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.symbol").value("005930"))
        .andExpect(jsonPath("$.data.avgPrice").value(70000.0))
        .andExpect(jsonPath("$.data.marketPrice").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.quoteSnapshotId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.quoteAsOf").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.quoteSourceMode").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.unrealizedPnl").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.realizedPnlDaily").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.valuationStatus").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.data.valuationUnavailableReason").value("QUOTE_MISSING"));
  }

  @Test
  void shouldReturnUnavailableValuationWhenQuoteProviderIsUnavailable() throws Exception {
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshot(eq("005930"), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenThrow(new BusinessException(
            ErrorCode.FEP_GATEWAY_UNAVAILABLE,
            ErrorCode.FEP_GATEWAY_UNAVAILABLE.defaultMessage()
        ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-position-provider-unavailable")
            .param("memberId", "1")
            .param("symbol", "005930"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-position-provider-unavailable"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.symbol").value("005930"))
        .andExpect(jsonPath("$.data.avgPrice").value(70000.0))
        .andExpect(jsonPath("$.data.marketPrice").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.quoteSnapshotId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.quoteAsOf").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.quoteSourceMode").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.unrealizedPnl").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.realizedPnlDaily").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.valuationStatus").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.data.valuationUnavailableReason").value("PROVIDER_UNAVAILABLE"));
  }

  @Test
  void shouldReturnProviderUnavailableValuationForOwnedAccountPositionsList() throws Exception {
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshots(anyList(), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenThrow(new BusinessException(
            ErrorCode.FEP_GATEWAY_UNAVAILABLE,
            ErrorCode.FEP_GATEWAY_UNAVAILABLE.defaultMessage()
        ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions/list", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-positions-provider-unavailable")
            .param("memberId", "1"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-positions-provider-unavailable"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].symbol").value("005930"))
        .andExpect(jsonPath("$.data[0].avgPrice").value(70000.0))
        .andExpect(jsonPath("$.data[0].marketPrice").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[0].quoteSnapshotId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[0].quoteAsOf").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[0].quoteSourceMode").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[0].unrealizedPnl").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[0].realizedPnlDaily").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data[0].valuationStatus").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.data[0].valuationUnavailableReason").value("PROVIDER_UNAVAILABLE"));
  }

  @Test
  void shouldReturnForbiddenWhenOwnershipMismatches() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-position-forbidden")
            .param("memberId", "2")
            .param("symbol", "005930"))
        .andExpect(status().isForbidden())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-position-forbidden"))
        .andExpect(jsonPath("$.code").value("AUTH-005"))
        .andExpect(jsonPath("$.message").value("forbidden account ownership"))
        .andExpect(jsonPath("$.path").value("/internal/v1/accounts/1/positions"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 999L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "1")
            .param("symbol", "005930"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CORE_001"))
        .andExpect(jsonPath("$.message").value("account not found"))
        .andExpect(jsonPath("$.path").value("/internal/v1/accounts/999/positions"));
  }

  private FepQuoteSnapshotResult quoteSnapshot(
      String quoteSnapshotId,
      String symbol,
      Instant quoteAsOf,
      Long bestBid,
      Long bestAsk,
      Long lastTrade
  ) {
    return new FepQuoteSnapshotResult(
        quoteSnapshotId,
        symbol,
        FepQuoteSourceMode.LIVE,
        quoteAsOf,
        bestBid,
        bestAsk,
        lastTrade,
        42L,
        false
    );
  }

  private FepQuoteSnapshotResult samsungSnapshot() {
    return quoteSnapshot(
        "qsnap-005930-live-001",
        "005930",
        FIXED_FRESH_QUOTE_AS_OF,
        72000L,
        72100L,
        72050L
    );
  }

  private FepQuoteSnapshotResult hynixSnapshot() {
    return quoteSnapshot(
        "qsnap-000660-live-001",
        "000660",
        FIXED_FRESH_QUOTE_AS_OF,
        120000L,
        120500L,
        120250L
    );
  }

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    Clock quoteFreshnessClock() {
      return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }
  }

  private void insertPosition(String symbol, String quantity, String avgPrice, String updatedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO positions (account_id, symbol, qty, avg_price, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        1L,
        symbol,
        new java.math.BigDecimal(quantity),
        new java.math.BigDecimal(avgPrice),
        Timestamp.from(Instant.parse(updatedAt)),
        Timestamp.from(Instant.parse(updatedAt)),
        0
    );
  }
}
