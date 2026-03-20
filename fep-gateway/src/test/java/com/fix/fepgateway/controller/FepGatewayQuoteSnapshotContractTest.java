package com.fix.fepgateway.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.web.CommonHeaders;
import com.fix.fepgateway.entity.QuoteSnapshot;
import com.fix.fepgateway.repository.QuoteSnapshotRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FepGatewayQuoteSnapshotContractTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private QuoteSnapshotRepository quoteSnapshotRepository;

  @BeforeEach
  void setUp() {
    quoteSnapshotRepository.deleteAll();
  }

  @Test
  void shouldReturnLatestSnapshotUsingQuoteAsOfAndStreamOffsetOrdering() throws Exception {
    Instant quoteAsOf = Instant.parse("2026-03-20T09:10:00Z");
    quoteSnapshotRepository.save(QuoteSnapshot.recorded(
        "qsnap-live-001",
        "005930",
        FepQuoteSourceMode.LIVE,
        quoteAsOf.minusSeconds(5),
        70000L,
        70100L,
        70050L,
        10L,
        false
    ));
    quoteSnapshotRepository.save(QuoteSnapshot.recorded(
        "qsnap-live-002",
        "005930",
        FepQuoteSourceMode.LIVE,
        quoteAsOf,
        70200L,
        70300L,
        70250L,
        11L,
        false
    ));
    quoteSnapshotRepository.save(QuoteSnapshot.recorded(
        "qsnap-live-003",
        "005930",
        FepQuoteSourceMode.LIVE,
        quoteAsOf,
        70400L,
        70500L,
        70450L,
        12L,
        false
    ));
    quoteSnapshotRepository.save(QuoteSnapshot.recorded(
        "qsnap-replay-001",
        "005930",
        FepQuoteSourceMode.REPLAY,
        quoteAsOf.plusSeconds(5),
        70600L,
        70700L,
        70650L,
        13L,
        false
    ));

    mockMvc.perform(get("/fep-internal/v1/quotes/snapshots/latest")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "corr-quote-latest")
            .queryParam("symbol", "005930")
            .queryParam("quoteSourceMode", "LIVE"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-quote-latest"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.quoteSnapshotId").value("qsnap-live-003"))
        .andExpect(jsonPath("$.data.symbol").value("005930"))
        .andExpect(jsonPath("$.data.quoteSourceMode").value("LIVE"))
        .andExpect(jsonPath("$.data.quoteAsOf").value("2026-03-20T09:10:00Z"))
        .andExpect(jsonPath("$.data.bestBid").value(70400))
        .andExpect(jsonPath("$.data.bestAsk").value(70500))
        .andExpect(jsonPath("$.data.lastTrade").value(70450))
        .andExpect(jsonPath("$.data.streamOffset").value(12))
        .andExpect(jsonPath("$.data.stale").value(false));
  }

  @Test
  void shouldReturnNotFoundWhenSnapshotDoesNotExist() throws Exception {
    mockMvc.perform(get("/fep-internal/v1/quotes/snapshots/latest")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "corr-quote-missing")
            .queryParam("symbol", "005930")
            .queryParam("quoteSourceMode", "LIVE"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("quote snapshot not found"))
        .andExpect(jsonPath("$.path").value("/fep-internal/v1/quotes/snapshots/latest"));
  }
}
