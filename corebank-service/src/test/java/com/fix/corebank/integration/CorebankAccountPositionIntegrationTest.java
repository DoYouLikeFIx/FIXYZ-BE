package com.fix.corebank.integration;

import static org.mockito.ArgumentMatchers.anyString;
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
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
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

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private FepQuoteSnapshotClient fepQuoteSnapshotClient;

  @BeforeEach
  void setUpQuoteSnapshots() {
    reset(fepQuoteSnapshotClient);
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshot(eq("005930"), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenReturn(quoteSnapshot(
            "qsnap-005930-live-001",
            "005930",
            Instant.now().minusSeconds(1),
            72000L,
            72100L,
            72050L
        ));
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshot(eq("000660"), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenReturn(quoteSnapshot(
            "qsnap-000660-live-001",
            "000660",
            Instant.now().minusSeconds(1),
            120000L,
            120500L,
            120250L
        ));
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
        .andExpect(jsonPath("$.data.marketPrice").value(72050.0))
        .andExpect(jsonPath("$.data.quoteSnapshotId").value("qsnap-005930-live-001"))
        .andExpect(jsonPath("$.data.quoteAsOf").isNotEmpty())
        .andExpect(jsonPath("$.data.quoteSourceMode").value("LIVE"));
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
        .andExpect(jsonPath("$.data[0].marketPrice").value(72050.0))
        .andExpect(jsonPath("$.data[0].quoteSnapshotId").value("qsnap-005930-live-001"))
        .andExpect(jsonPath("$.data[0].quoteSourceMode").value("LIVE"));
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
        .andExpect(jsonPath("$.data.asOf").isNotEmpty());
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
        .andExpect(jsonPath("$.data.marketPrice").value(120250.0))
        .andExpect(jsonPath("$.data.quoteSnapshotId").value("qsnap-000660-live-001"))
        .andExpect(jsonPath("$.data.quoteSourceMode").value("LIVE"));
  }

  @Test
  void shouldReturnStaleQuoteEnvelopeWhenSnapshotIsTooOld() throws Exception {
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshot(eq("005930"), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenReturn(quoteSnapshot(
            "qsnap-005930-stale-001",
            "005930",
            Instant.now().minusMillis(6_000L),
            72000L,
            72100L,
            72050L
        ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-position-stale")
            .param("memberId", "1")
            .param("symbol", "005930"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-position-stale"))
        .andExpect(jsonPath("$.code").value(ErrorCode.STALE_QUOTE.code()))
        .andExpect(jsonPath("$.message").value(ErrorCode.STALE_QUOTE.defaultMessage()))
        .andExpect(jsonPath("$.details.symbol").value("005930"))
        .andExpect(jsonPath("$.details.snapshotAgeMs").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6000)))
        .andExpect(jsonPath("$.details.quoteSnapshotId").value("qsnap-005930-stale-001"))
        .andExpect(jsonPath("$.details.quoteSourceMode").value("LIVE"));
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
}
