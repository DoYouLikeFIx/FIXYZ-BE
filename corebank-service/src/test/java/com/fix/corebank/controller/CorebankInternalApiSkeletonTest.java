package com.fix.corebank.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.valuation.ValuationStatus;
import com.fix.common.web.CommonHeaders;
import com.fix.corebank.filter.CorrelationIdFilter;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.ExecutionRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.service.AccountProvisioningService;
import com.fix.corebank.service.CorebankOrderPersistenceService;
import com.fix.corebank.service.CorebankOrderReplayService;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.service.LedgerIntegrityObservabilityService;
import com.fix.corebank.service.LedgerReconciliationService;
import com.fix.corebank.service.LedgerRepairService;
import com.fix.corebank.service.PositionLockMetrics;
import com.fix.corebank.support.CorebankStandaloneMvcSupport;
import com.fix.corebank.vo.AccountProvisioningCommand;
import com.fix.corebank.vo.AccountProvisioningResult;
import com.fix.corebank.vo.AccountPositionQueryCommand;
import com.fix.corebank.vo.AccountPositionsQueryCommand;
import com.fix.corebank.vo.AccountPositionResult;
import com.fix.corebank.vo.AccountStatusQueryCommand;
import com.fix.corebank.vo.AccountStatusResult;
import com.fix.corebank.vo.AccountStatusTransitionCommand;
import com.fix.corebank.vo.AccountStatusTransitionResult;
import com.fix.corebank.vo.AccountSummaryQueryCommand;
import com.fix.corebank.vo.AccountOrderHistoryQueryCommand;
import com.fix.corebank.vo.AccountOrderHistoryResult;
import com.fix.corebank.vo.AccountOrderHistoryItemResult;
import com.fix.corebank.vo.InternalOrderResult;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderReplayCommand;
import com.fix.corebank.vo.InternalOrderReplayResult;
import com.fix.corebank.vo.InternalOrderRequeryCommand;
import com.fix.corebank.vo.LedgerIntegrityFailedIdentifier;
import com.fix.corebank.vo.LedgerIntegrityObservabilitySummary;
import com.fix.corebank.vo.InternalOrderSnapshotResult;
import com.fix.corebank.vo.PortfolioQueryCommand;
import com.fix.corebank.vo.PortfolioResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class CorebankInternalApiSkeletonTest {

  private static final String CORE_CL_ORD_ID_1 = "123e4567-e89b-42d3-a456-426614174210";
  private static final String CORE_CL_ORD_ID_2 = "123e4567-e89b-42d3-a456-426614174211";
  private static final String CORE_CL_ORD_ID_3 = "123e4567-e89b-42d3-a456-426614174212";

  private MockMvc mockMvc;
  private StubCorebankOrderService corebankOrderService;
  private StubAccountProvisioningService accountProvisioningService;
  private LedgerIntegrityObservabilityService ledgerIntegrityObservabilityService;
  private CorebankOrderReplayService corebankOrderReplayService;

  @BeforeEach
  void setUp() {
    corebankOrderService = new StubCorebankOrderService();
    accountProvisioningService = new StubAccountProvisioningService();
    ledgerIntegrityObservabilityService = mock(LedgerIntegrityObservabilityService.class);
    corebankOrderReplayService = mock(CorebankOrderReplayService.class);
    mockMvc = CorebankStandaloneMvcSupport.build(
        List.of(
            new CorrelationIdFilter(),
            new com.fix.corebank.security.InternalSecretFilter(
                "test-secret",
                JsonMapper.builder().findAndAddModules().build()
            )
        ),
        new InternalCorebankController(
            corebankOrderService,
            corebankOrderReplayService,
            accountProvisioningService,
            ledgerIntegrityObservabilityService,
            mock(LedgerReconciliationService.class),
            mock(LedgerRepairService.class)
        )
    );
  }

  @Test
  void shouldSupportInternalPortfolioAndOrderEndpoints() throws Exception {
    String correlationId = "66cf95fd-3660-48a6-9e71-7ed42257b748";
    accountProvisioningService.setProvisioningResults(
        AccountProvisioningResult.of(
            1L,
            "11000000000301",
            "ACTIVE",
            false,
            301L,
            Instant.parse("2026-03-01T10:00:00Z")
        ),
        AccountProvisioningResult.of(
            1L,
            "11000000000301",
            "ACTIVE",
            true,
            301L,
            Instant.parse("2026-03-01T10:00:00Z")
        )
    );

    corebankOrderService.setPortfolioResult(PortfolioResult.of(
        1L,
        "ACC-1001",
        "005930",
        new BigDecimal("120.0000"),
        new BigDecimal("500.0000"),
        BigDecimal.ZERO
    ));
    corebankOrderService.setAccountPositionResult(AccountPositionResult.of(
        1L,
        301L,
        "005930",
        new BigDecimal("120.0000"),
        new BigDecimal("120.0000"),
        new BigDecimal("1000000.0000"),
        "KRW",
        Instant.parse("2026-03-01T10:01:00Z"),
        new BigDecimal("70000.0000"),
        new BigDecimal("72050.0000"),
        "qsnap-005930-live-001",
        Instant.parse("2026-03-01T10:00:59Z"),
        FepQuoteSourceMode.LIVE,
        new BigDecimal("246000.0000"),
        new BigDecimal("5000.0000"),
        ValuationStatus.FRESH,
        null
    ));
    corebankOrderService.setAccountSummaryResult(AccountPositionResult.of(
        1L,
        301L,
        "",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("1000000.0000"),
        "KRW",
        Instant.parse("2026-03-01T10:01:00Z")
    ));
    corebankOrderService.setAccountPositionsResult(List.of(
        AccountPositionResult.of(
            1L,
            301L,
            "000660",
            new BigDecimal("15.0000"),
            new BigDecimal("15.0000"),
            new BigDecimal("98500000.0000"),
            "KRW",
            Instant.parse("2026-03-01T10:01:30Z"),
            new BigDecimal("120000.0000"),
            new BigDecimal("120250.0000"),
            "qsnap-000660-live-001",
            Instant.parse("2026-03-01T10:01:20Z"),
            FepQuoteSourceMode.LIVE,
            new BigDecimal("3750.0000"),
            BigDecimal.ZERO.setScale(4),
            ValuationStatus.FRESH,
            null
        ),
        AccountPositionResult.of(
            1L,
            301L,
            "005930",
            new BigDecimal("120.0000"),
            new BigDecimal("120.0000"),
            new BigDecimal("1000000.0000"),
            "KRW",
            Instant.parse("2026-03-01T10:01:00Z"),
            new BigDecimal("70000.0000"),
            new BigDecimal("72050.0000"),
            "qsnap-005930-live-001",
            Instant.parse("2026-03-01T10:00:59Z"),
            FepQuoteSourceMode.LIVE,
            new BigDecimal("246000.0000"),
            new BigDecimal("5000.0000"),
            ValuationStatus.FRESH,
            null
        )
    ));
    corebankOrderService.setAccountStatusResult(AccountStatusResult.of(
        1L,
        301L,
        "11000000000301",
        "ACTIVE",
        true,
        null,
        Instant.parse("2026-03-01T10:01:00Z")
    ));
    corebankOrderService.setDefaultAccountStatusResult(AccountStatusResult.of(
        1L,
        301L,
        "11000000000301",
        "ACTIVE",
        true,
        null,
        Instant.parse("2026-03-01T10:01:00Z")
    ));
    corebankOrderService.setAccountOrderHistoryResult(AccountOrderHistoryResult.of(
        List.of(
            AccountOrderHistoryItemResult.of(
                "005930",
                "삼성전자",
                "BUY",
                new BigDecimal("2.0000"),
                new BigDecimal("70100.0000"),
                new BigDecimal("140200.00000000"),
                "FILLED",
                CORE_CL_ORD_ID_1,
                Instant.parse("2026-03-01T10:02:00Z")
            )
        ),
        1L,
        1,
        0,
        20
    ));
    corebankOrderService.setCreateOrderResult(InternalOrderResult.execution(
        1001L,
        CORE_CL_ORD_ID_1,
        "FILLED",
        "CONFIRMED",
        false,
        new BigDecimal("2.0000"),
        "FILLED",
        new BigDecimal("2.0000"),
        BigDecimal.ZERO,
        new BigDecimal("70100.0000"),
        "FEP-KRX-" + CORE_CL_ORD_ID_1,
        Instant.parse("2026-03-01T10:02:30Z")
    ));
    corebankOrderService.setRequeryOrderResult(InternalOrderResult.requery(
        1001L,
        CORE_CL_ORD_ID_1,
        "FILLED",
        "CONFIRMED",
        true,
        new BigDecimal("2.0000"),
        "FILLED",
        new BigDecimal("2.0000"),
        BigDecimal.ZERO,
        new BigDecimal("70100.0000"),
        "FEP-KRX-" + CORE_CL_ORD_ID_1,
        Instant.parse("2026-03-01T10:02:30Z"),
        "exchange confirmed",
        false,
        false,
        1,
        5
    ));
    corebankOrderService.setOrderSnapshotResult(InternalOrderSnapshotResult.of(
        1001L,
        1L,
        CORE_CL_ORD_ID_1,
        "FILLED",
        "CONFIRMED",
        "FEP-KRX-" + CORE_CL_ORD_ID_1
    ));
    corebankOrderService.expectOrderSnapshotClOrdId(CORE_CL_ORD_ID_1);
    corebankOrderService.setAccountStatusTransitionResult(AccountStatusTransitionResult.of(
        1L,
        301L,
        "ACTIVE",
        "FROZEN",
        true,
        9001L,
        "risk-control",
        "ops-admin",
        "ticket=FIX-43",
        Instant.parse("2026-03-01T10:03:00Z")
    ));

    mockMvc.perform(post("/internal/v1/portfolio")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, correlationId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "memberId": 301,
                  "memberNo": "M-301",
                  "email": "member301@fix.local"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.memberId").value(301L))
        .andExpect(jsonPath("$.data.idempotent").value(false));

    mockMvc.perform(post("/internal/v1/portfolio")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, correlationId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "memberId": 301,
                  "memberNo": "M-301",
                  "email": "member301@fix.local"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memberId").value(301L))
        .andExpect(jsonPath("$.data.idempotent").value(true));

    mockMvc.perform(get("/internal/v1/portfolio")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("symbol", "005930"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.symbol").value("005930"));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301")
            .param("symbol", "005930"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.memberId").value(301L))
        .andExpect(jsonPath("$.data.symbol").value("005930"))
        .andExpect(jsonPath("$.data.availableQuantity").value(120.0))
        .andExpect(jsonPath("$.data.availableQty").value(120.0))
        .andExpect(jsonPath("$.data.balance").value(1000000.0))
        .andExpect(jsonPath("$.data.availableBalance").value(1000000.0))
        .andExpect(jsonPath("$.data.currency").value("KRW"))
        .andExpect(jsonPath("$.data.marketPrice").value(72050.0))
        .andExpect(jsonPath("$.data.quoteSnapshotId").value("qsnap-005930-live-001"))
        .andExpect(jsonPath("$.data.quoteAsOf").exists())
        .andExpect(jsonPath("$.data.quoteSourceMode").value("LIVE"));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/summary", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.memberId").value(301L))
        .andExpect(jsonPath("$.data.symbol").value(""))
        .andExpect(jsonPath("$.data.balance").value(1000000.0))
        .andExpect(jsonPath("$.data.marketPrice").doesNotExist())
        .andExpect(jsonPath("$.data.valuationStatus").doesNotExist());

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions/list", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].symbol").value("000660"))
        .andExpect(jsonPath("$.data[0].marketPrice").value(120250.0))
        .andExpect(jsonPath("$.data[0].quoteSnapshotId").value("qsnap-000660-live-001"))
        .andExpect(jsonPath("$.data[1].symbol").value("005930"))
        .andExpect(jsonPath("$.data[1].marketPrice").value(72050.0))
        .andExpect(jsonPath("$.data[1].quoteSourceMode").value("LIVE"));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/status", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.memberId").value(301L))
        .andExpect(jsonPath("$.data.accountNumber").value("11000000000301"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.orderEligible").value(true));

    mockMvc.perform(get("/internal/v1/accounts/default")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.memberId").value(301L))
        .andExpect(jsonPath("$.data.accountNumber").value("11000000000301"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/orders", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301")
            .param("page", "0")
            .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].symbol").value("005930"))
        .andExpect(jsonPath("$.data.content[0].symbolName").value("삼성전자"))
        .andExpect(jsonPath("$.data.content[0].qty").value(2.0))
        .andExpect(jsonPath("$.data.content[0].unitPrice").value(70100.0))
        .andExpect(jsonPath("$.data.content[0].totalAmount").value(140200.0))
        .andExpect(jsonPath("$.data.content[0].clOrdId").value(CORE_CL_ORD_ID_1))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.number").value(0))
        .andExpect(jsonPath("$.data.size").value(20));

    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("clOrdId", CORE_CL_ORD_ID_1)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CORE_CL_ORD_ID_1))
        .andExpect(jsonPath("$.data.status").value("FILLED"))
        .andExpect(jsonPath("$.data.externalSyncStatus").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.executionResult").value("FILLED"))
        .andExpect(jsonPath("$.data.executedQty").value(2.0))
        .andExpect(jsonPath("$.data.leavesQty").value(0.0))
        .andExpect(jsonPath("$.data.executedPrice").value(70100.0))
        .andExpect(jsonPath("$.data.externalOrderId").value("FEP-KRX-" + CORE_CL_ORD_ID_1))
        .andExpect(jsonPath("$.data.executedAt").exists());

    mockMvc.perform(get("/internal/v1/orders/{clOrdId}", CORE_CL_ORD_ID_1)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orderId").value(1001L))
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.clOrdId").value(CORE_CL_ORD_ID_1))
        .andExpect(jsonPath("$.data.status").value("FILLED"))
        .andExpect(jsonPath("$.data.externalSyncStatus").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.externalOrderId").value("FEP-KRX-" + CORE_CL_ORD_ID_1));

    mockMvc.perform(get("/internal/v1/orders/{clOrdId}/requery", CORE_CL_ORD_ID_1)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("attemptCount", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CORE_CL_ORD_ID_1))
        .andExpect(jsonPath("$.data.status").value("FILLED"))
        .andExpect(jsonPath("$.data.executionResult").value("FILLED"))
        .andExpect(jsonPath("$.data.externalOrderId").value("FEP-KRX-" + CORE_CL_ORD_ID_1))
        .andExpect(jsonPath("$.data.message").value("exchange confirmed"));

    when(corebankOrderReplayService.replay(any(InternalOrderReplayCommand.class))).thenReturn(InternalOrderReplayResult.of(
        CORE_CL_ORD_ID_1,
        "COMPLETED",
        "FILLED",
        "VIRTUAL_FILL",
        new BigDecimal("2.0000"),
        BigDecimal.ZERO,
        new BigDecimal("70100.0000"),
        "FEP-KRX-" + CORE_CL_ORD_ID_1,
        "CONFIRMED",
        Instant.parse("2026-03-01T10:02:30Z"),
        null,
        "123e4567-e89b-42d3-a456-426614174299",
        Instant.parse("2026-03-01T10:05:00Z")
    ));

    mockMvc.perform(post("/internal/v1/orders/{clOrdId}/replay", CORE_CL_ORD_ID_1)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, correlationId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "123e4567-e89b-42d3-a456-426614174299",
                  "approvedBy": "123e4567-e89b-42d3-a456-426614174298",
                  "evidenceRef": "OPS-INC-42",
                  "reason": "KRX outage resolved after manual exchange confirmation",
                  "executionPrice": 70100
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CORE_CL_ORD_ID_1))
        .andExpect(jsonPath("$.data.finalStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.executionSource").value("VIRTUAL_FILL"))
        .andExpect(jsonPath("$.data.processedBy").value("123e4567-e89b-42d3-a456-426614174299"));

    mockMvc.perform(patch("/internal/v1/accounts/{accountId}/status", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, correlationId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "memberId": 301,
                  "status": "FROZEN",
                  "reason": "risk-control",
                  "actor": "ops-admin",
                  "context": "ticket=FIX-43"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.previousStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data.newStatus").value("FROZEN"))
        .andExpect(jsonPath("$.data.changed").value(true))
        .andExpect(jsonPath("$.data.eventId").value(9001L));
  }

  @Test
  void shouldMapOwnershipFailureForAccountPositionEndpoint() throws Exception {
    corebankOrderService.setAccountPositionFailure(new BusinessException(
        ErrorCode.AUTH_FORBIDDEN_OWNERSHIP,
        "forbidden account ownership"
    ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301")
            .param("symbol", "005930"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP.code()));
  }

  @Test
  void shouldMapStaleQuoteFailureForAccountPositionEndpoint() throws Exception {
    corebankOrderService.setAccountPositionFailure(new BusinessException(
        ErrorCode.STALE_QUOTE,
        ErrorCode.STALE_QUOTE.defaultMessage(),
        null,
        Map.of(
            "symbol", "005930",
            "snapshotAgeMs", 6000,
            "quoteSourceMode", "LIVE"
        )
    ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301")
            .param("symbol", "005930"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ErrorCode.STALE_QUOTE.code()))
        .andExpect(jsonPath("$.details.symbol").value("005930"))
        .andExpect(jsonPath("$.details.snapshotAgeMs").value(6000))
        .andExpect(jsonPath("$.details.quoteSourceMode").value("LIVE"));
  }

  @Test
  void shouldReturnLedgerIntegritySummaryForInternalCaller() throws Exception {
    LedgerIntegrityFailedIdentifier identifier = mock(LedgerIntegrityFailedIdentifier.class);
    when(identifier.getAnomalyId()).thenReturn(801L);
    when(identifier.getAnomalyType()).thenReturn("NEGATIVE_POSITION");
    when(identifier.getClOrdId()).thenReturn(CORE_CL_ORD_ID_1);

    when(ledgerIntegrityObservabilityService.readSummary()).thenReturn(
        LedgerIntegrityObservabilitySummary.of(
            72L,
            Instant.parse("2026-03-01T10:10:00Z"),
            true,
            0,
            "Ledger integrity check passed",
            1L,
            1L,
            1L,
            false,
            71L,
            List.of(identifier)
        )
    );

    mockMvc.perform(get("/internal/v1/ledger-integrity/summary")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.latestRunId").value(72L))
        .andExpect(jsonPath("$.data.latestRunPassed").value(true))
        .andExpect(jsonPath("$.data.latestRunAnomalyCount").value(0))
        .andExpect(jsonPath("$.data.unresolvedAnomalyCount").value(1))
        .andExpect(jsonPath("$.data.repairPendingCount").value(1))
        .andExpect(jsonPath("$.data.criticalAnomalyCount").value(1))
        .andExpect(jsonPath("$.data.staleLastRun").value(false))
        .andExpect(jsonPath("$.data.latestFailedRunId").value(71L))
        .andExpect(jsonPath("$.data.latestFailedIdentifiers[0].anomalyId").value(801L))
        .andExpect(jsonPath("$.data.latestFailedIdentifiers[0].anomalyType").value("NEGATIVE_POSITION"))
        .andExpect(jsonPath("$.data.latestFailedIdentifiers[0].clOrdId").value(CORE_CL_ORD_ID_1));
  }

  @Test
  void shouldReturnEmptyLedgerIntegritySummaryForInternalCaller() throws Exception {
    when(ledgerIntegrityObservabilityService.readSummary()).thenReturn(
        LedgerIntegrityObservabilitySummary.of(
            null,
            null,
            null,
            null,
            null,
            0L,
            0L,
            0L,
            true,
            null,
            List.of()
        )
    );

    mockMvc.perform(get("/internal/v1/ledger-integrity/summary")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.latestRunId").doesNotExist())
        .andExpect(jsonPath("$.data.latestRunCheckedAt").doesNotExist())
        .andExpect(jsonPath("$.data.latestRunPassed").doesNotExist())
        .andExpect(jsonPath("$.data.latestRunAnomalyCount").doesNotExist())
        .andExpect(jsonPath("$.data.latestRunSummaryMessage").doesNotExist())
        .andExpect(jsonPath("$.data.unresolvedAnomalyCount").value(0))
        .andExpect(jsonPath("$.data.repairPendingCount").value(0))
        .andExpect(jsonPath("$.data.criticalAnomalyCount").value(0))
        .andExpect(jsonPath("$.data.staleLastRun").value(true))
        .andExpect(jsonPath("$.data.latestFailedRunId").doesNotExist())
        .andExpect(jsonPath("$.data.latestFailedIdentifiers").isEmpty());
  }

  @Test
  void shouldRejectLedgerIntegritySummaryWithoutInternalSecret() throws Exception {
    mockMvc.perform(get("/internal/v1/ledger-integrity/summary"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_REQUIRED.code()))
        .andExpect(jsonPath("$.message").value("Missing or invalid X-Internal-Secret"));
  }

  @Test
  void shouldMapOwnershipFailureForAccountPositionsEndpoint() throws Exception {
    corebankOrderService.setAccountPositionsFailure(new BusinessException(
        ErrorCode.AUTH_FORBIDDEN_OWNERSHIP,
        "forbidden account ownership"
    ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions/list", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP.code()));
  }

  @Test
  void shouldMapOwnershipFailureForAccountSummaryEndpoint() throws Exception {
    corebankOrderService.setAccountSummaryFailure(new BusinessException(
        ErrorCode.AUTH_FORBIDDEN_OWNERSHIP,
        "forbidden account ownership"
    ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/summary", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP.code()));
  }

  @Test
  void shouldMapNotFoundFailureForAccountPositionEndpoint() throws Exception {
    corebankOrderService.setAccountPositionFailure(new BusinessException(
        ErrorCode.CORE_RESOURCE_NOT_FOUND,
        "account not found"
    ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/positions", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301")
            .param("symbol", "005930"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(ErrorCode.CORE_RESOURCE_NOT_FOUND.code()));
  }

  @Test
  void shouldMapOwnershipFailureForAccountStatusEndpoint() throws Exception {
    corebankOrderService.setAccountStatusFailure(new BusinessException(
        ErrorCode.AUTH_FORBIDDEN_OWNERSHIP,
        "forbidden account ownership"
    ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/status", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP.code()));
  }

  @Test
  void shouldMapOwnershipFailureForAccountStatusTransitionEndpoint() throws Exception {
    corebankOrderService.setAccountStatusTransitionFailure(new BusinessException(
        ErrorCode.AUTH_FORBIDDEN_OWNERSHIP,
        "forbidden account ownership"
    ));

    mockMvc.perform(patch("/internal/v1/accounts/{accountId}/status", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-transition-forbidden")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "memberId": 301,
                  "status": "FROZEN",
                  "reason": "risk-control",
                  "actor": "ops-admin"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP.code()));
  }

  @Test
  void shouldMapOwnershipFailureForAccountOrderHistoryEndpoint() throws Exception {
    corebankOrderService.setAccountOrderHistoryFailure(new BusinessException(
        ErrorCode.AUTH_FORBIDDEN_OWNERSHIP,
        "forbidden account ownership"
    ));

    mockMvc.perform(get("/internal/v1/accounts/{accountId}/orders", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301")
            .param("page", "0")
            .param("size", "20"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP.code()));
  }

  @Test
  void shouldValidatePaginationForAccountOrderHistoryEndpoint() throws Exception {
    mockMvc.perform(get("/internal/v1/accounts/{accountId}/orders", 1L)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("memberId", "301")
            .param("page", "-1")
            .param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void shouldExposeSchedulerRequeryMetadataAndValidateAttemptCount() throws Exception {
    corebankOrderService.setRequeryOrderResult(InternalOrderResult.requery(
        1002L,
        CORE_CL_ORD_ID_2,
        "UNKNOWN",
        true,
        new BigDecimal("2.0000"),
        "still unresolved",
        false,
        true,
        5,
        5
    ));

    mockMvc.perform(get("/internal/v1/orders/{clOrdId}/requery", CORE_CL_ORD_ID_2)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("attemptCount", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CORE_CL_ORD_ID_2))
        .andExpect(jsonPath("$.data.status").value("UNKNOWN"))
        .andExpect(jsonPath("$.data.message").value("still unresolved"))
        .andExpect(jsonPath("$.data.retriable").value(false))
        .andExpect(jsonPath("$.data.escalationRequired").value(true))
        .andExpect(jsonPath("$.data.attemptCount").value(5))
        .andExpect(jsonPath("$.data.maxRetryCount").value(5));

    mockMvc.perform(get("/internal/v1/orders/{clOrdId}/requery", CORE_CL_ORD_ID_2)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("attemptCount", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void shouldRejectFractionalOrderInputsBeforeCallingFepClient() throws Exception {
    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("clOrdId", CORE_CL_ORD_ID_2)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.5000")
            .param("price", "70100.1000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

    org.assertj.core.api.Assertions.assertThat(corebankOrderService.createOrderCalls()).isZero();
  }

  @Test
  void shouldRejectMarketOrderWhenQuoteContextIsMissing() throws Exception {
    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("clOrdId", CORE_CL_ORD_ID_2)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("orderType", "MARKET")
            .param("quantity", "2.0000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

    org.assertj.core.api.Assertions.assertThat(corebankOrderService.createOrderCalls()).isZero();
  }

  @Test
  void shouldAcceptMarketOrderRequestWithQuoteContext() throws Exception {
    corebankOrderService.setCreateOrderResult(InternalOrderResult.execution(
        77L,
        CORE_CL_ORD_ID_2,
        "PENDING",
        "CONFIRMED",
        false,
        BigDecimal.valueOf(2),
        "FILLED",
        BigDecimal.valueOf(2),
        BigDecimal.ZERO,
        BigDecimal.valueOf(70100),
        "FEP-77",
        Instant.parse("2026-03-21T00:00:01Z")
    ));

    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("clOrdId", CORE_CL_ORD_ID_2)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("orderType", "MARKET")
            .param("quantity", "2.0000")
            .param("quoteSnapshotId", "qsnap-20260321-0003")
            .param("quoteAsOf", "2026-03-21T00:00:00Z")
            .param("quoteSourceMode", "LIVE")
            .param("preTradePrice", "70100.0000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    org.assertj.core.api.Assertions.assertThat(corebankOrderService.createOrderCalls()).isEqualTo(1);
  }

  @Test
  void shouldReturn422WhenProvisioningPayloadMissesMemberId() throws Exception {
    accountProvisioningService.setProvisioningFailure(new BusinessException(
        ErrorCode.CONTRACT_VALIDATION_FAILED,
        "memberId is required"
    ));

    mockMvc.perform(post("/internal/v1/portfolio")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "a2d4c77d-7449-44ff-bec8-f2c1cf9f512c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "memberNo": "M-INVALID",
                  "email": "member-invalid@fix.local"
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldExposeUserMessageKeyAndOperatorCodeForMappedExternalErrors() throws Exception {
    corebankOrderService.setCreateOrderFailure(new BusinessException(
        ErrorCode.FEP_GATEWAY_TIMEOUT,
        ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage(),
        new ErrorMetadata("error.fep.timeout", "TIMEOUT"),
        Map.of("symbol", "005930", "requestedQty", 2)
    ));

    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("clOrdId", CORE_CL_ORD_ID_3)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("FEP-002"))
        .andExpect(jsonPath("$.userMessageKey").value("error.fep.timeout"))
        .andExpect(jsonPath("$.operatorCode").value("TIMEOUT"))
        .andExpect(jsonPath("$.details.symbol").value("005930"))
        .andExpect(jsonPath("$.details.requestedQty").value(2));
  }

  @Test
  void shouldExposeConflictEnvelopeForPositionLockContention() throws Exception {
    corebankOrderService.setCreateOrderFailure(new BusinessException(
        ErrorCode.CORE_CONCURRENCY_CONFLICT,
        ErrorCode.CORE_CONCURRENCY_CONFLICT.defaultMessage(),
        new ErrorMetadata("error.core.concurrency_conflict", "CONCURRENCY_FAILURE"),
        Map.of(
            "accountId", 1L,
            "symbol", "005930",
            "clOrdId", CORE_CL_ORD_ID_3,
            "failureReason", "POSITION_LOCK"
        )
    ));

    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-position-lock")
            .param("accountId", "1")
            .param("clOrdId", CORE_CL_ORD_ID_3)
            .param("symbol", "005930")
            .param("side", "SELL")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CORE-003"))
        .andExpect(jsonPath("$.message").value("Concurrent modification conflict"))
        .andExpect(jsonPath("$.userMessageKey").value("error.core.concurrency_conflict"))
        .andExpect(jsonPath("$.operatorCode").value("CONCURRENCY_FAILURE"))
        .andExpect(jsonPath("$.details.failureReason").value("POSITION_LOCK"))
        .andExpect(jsonPath("$.details.symbol").value("005930"))
        .andExpect(jsonPath("$.correlationId").value("trace-position-lock"));
  }

  private static final class StubCorebankOrderService extends CorebankOrderService {

    private PortfolioResult portfolioResult;
    private AccountPositionResult accountPositionResult;
    private RuntimeException accountPositionFailure;
    private List<AccountPositionResult> accountPositionsResult = List.of();
    private RuntimeException accountPositionsFailure;
    private AccountPositionResult accountSummaryResult;
    private RuntimeException accountSummaryFailure;
    private AccountStatusResult accountStatusResult;
    private AccountStatusResult defaultAccountStatusResult;
    private RuntimeException accountStatusFailure;
    private AccountStatusTransitionResult accountStatusTransitionResult;
    private RuntimeException accountStatusTransitionFailure;
    private AccountOrderHistoryResult accountOrderHistoryResult;
    private RuntimeException accountOrderHistoryFailure;
    private InternalOrderResult createOrderResult;
    private InternalOrderSnapshotResult orderSnapshotResult;
    private String expectedOrderSnapshotClOrdId;
    private InternalOrderResult requeryOrderResult;
    private RuntimeException createOrderFailure;
    private int createOrderCalls;

    private StubCorebankOrderService() {
      super(
          (AccountRepository) null,
          (PositionRepository) null,
          (ExecutionRepository) null,
          (CorebankOrderPersistenceService) null,
          null,
          null,
          null,
          null,
          null,
          new PositionLockMetrics(new SimpleMeterRegistry())
      );
    }

    @Override
    public PortfolioResult getPortfolio(PortfolioQueryCommand command) {
      return portfolioResult;
    }

    @Override
    public AccountPositionResult getAccountPosition(AccountPositionQueryCommand command) {
      if (accountPositionFailure != null) {
        throw accountPositionFailure;
      }
      return accountPositionResult;
    }

    @Override
    public AccountStatusResult getAccountStatus(AccountStatusQueryCommand command) {
      if (accountStatusFailure != null) {
        throw accountStatusFailure;
      }
      return accountStatusResult;
    }

    @Override
    public AccountStatusResult getDefaultAccountStatus(Long memberId) {
      if (accountStatusFailure != null) {
        throw accountStatusFailure;
      }
      return defaultAccountStatusResult;
    }

    @Override
    public AccountStatusTransitionResult transitionAccountStatus(AccountStatusTransitionCommand command) {
      if (accountStatusTransitionFailure != null) {
        throw accountStatusTransitionFailure;
      }
      return accountStatusTransitionResult;
    }

    @Override
    public List<AccountPositionResult> getAccountPositions(AccountPositionsQueryCommand command) {
      if (accountPositionsFailure != null) {
        throw accountPositionsFailure;
      }
      return accountPositionsResult;
    }

    @Override
    public AccountPositionResult getAccountSummary(AccountSummaryQueryCommand command) {
      if (accountSummaryFailure != null) {
        throw accountSummaryFailure;
      }
      return accountSummaryResult;
    }

    @Override
    public AccountOrderHistoryResult getAccountOrderHistory(AccountOrderHistoryQueryCommand command) {
      if (accountOrderHistoryFailure != null) {
        throw accountOrderHistoryFailure;
      }
      return accountOrderHistoryResult;
    }

    @Override
    public InternalOrderResult createOrder(InternalOrderCreateCommand command) {
      createOrderCalls++;
      if (createOrderFailure != null) {
        throw createOrderFailure;
      }
      return createOrderResult;
    }

    @Override
    public InternalOrderResult requeryOrder(InternalOrderRequeryCommand command) {
      return requeryOrderResult;
    }

    @Override
    public InternalOrderSnapshotResult getOrderSnapshot(String clOrdId) {
      if (expectedOrderSnapshotClOrdId != null) {
        org.assertj.core.api.Assertions.assertThat(clOrdId).isEqualTo(expectedOrderSnapshotClOrdId);
      }
      return orderSnapshotResult;
    }

    private void setPortfolioResult(PortfolioResult portfolioResult) {
      this.portfolioResult = portfolioResult;
    }

    private void setAccountPositionResult(AccountPositionResult accountPositionResult) {
      this.accountPositionResult = accountPositionResult;
    }

    private void setAccountPositionFailure(RuntimeException accountPositionFailure) {
      this.accountPositionFailure = accountPositionFailure;
    }

    private void setAccountStatusResult(AccountStatusResult accountStatusResult) {
      this.accountStatusResult = accountStatusResult;
    }

    private void setDefaultAccountStatusResult(AccountStatusResult defaultAccountStatusResult) {
      this.defaultAccountStatusResult = defaultAccountStatusResult;
    }

    private void setAccountStatusFailure(RuntimeException accountStatusFailure) {
      this.accountStatusFailure = accountStatusFailure;
    }

    private void setAccountStatusTransitionResult(AccountStatusTransitionResult accountStatusTransitionResult) {
      this.accountStatusTransitionResult = accountStatusTransitionResult;
    }

    private void setAccountStatusTransitionFailure(RuntimeException accountStatusTransitionFailure) {
      this.accountStatusTransitionFailure = accountStatusTransitionFailure;
    }

    private void setAccountPositionsResult(List<AccountPositionResult> accountPositionsResult) {
      this.accountPositionsResult = accountPositionsResult;
    }

    private void setAccountPositionsFailure(RuntimeException accountPositionsFailure) {
      this.accountPositionsFailure = accountPositionsFailure;
    }

    private void setAccountSummaryResult(AccountPositionResult accountSummaryResult) {
      this.accountSummaryResult = accountSummaryResult;
    }

    private void setAccountSummaryFailure(RuntimeException accountSummaryFailure) {
      this.accountSummaryFailure = accountSummaryFailure;
    }

    private void setAccountOrderHistoryResult(AccountOrderHistoryResult accountOrderHistoryResult) {
      this.accountOrderHistoryResult = accountOrderHistoryResult;
    }

    private void setAccountOrderHistoryFailure(RuntimeException accountOrderHistoryFailure) {
      this.accountOrderHistoryFailure = accountOrderHistoryFailure;
    }

    private void setCreateOrderResult(InternalOrderResult createOrderResult) {
      this.createOrderResult = createOrderResult;
    }

    private void setOrderSnapshotResult(InternalOrderSnapshotResult orderSnapshotResult) {
      this.orderSnapshotResult = orderSnapshotResult;
    }

    private void expectOrderSnapshotClOrdId(String expectedOrderSnapshotClOrdId) {
      this.expectedOrderSnapshotClOrdId = expectedOrderSnapshotClOrdId;
    }

    private void setRequeryOrderResult(InternalOrderResult requeryOrderResult) {
      this.requeryOrderResult = requeryOrderResult;
    }

    private void setCreateOrderFailure(RuntimeException createOrderFailure) {
      this.createOrderFailure = createOrderFailure;
    }

    private int createOrderCalls() {
      return createOrderCalls;
    }
  }

  private static final class StubAccountProvisioningService extends AccountProvisioningService {

    private final List<AccountProvisioningResult> results = new ArrayList<>();
    private RuntimeException provisioningFailure;
    private int nextIndex;

    private StubAccountProvisioningService() {
      super(null, null, null);
    }

    @Override
    public AccountProvisioningResult provisionDefaultAccount(AccountProvisioningCommand command) {
      if (provisioningFailure != null) {
        throw provisioningFailure;
      }
      if (nextIndex >= results.size()) {
        throw new IllegalStateException("No stub provisioning result configured");
      }
      return results.get(nextIndex++);
    }

    private void setProvisioningResults(AccountProvisioningResult... provisioningResults) {
      results.clear();
      results.addAll(List.of(provisioningResults));
      provisioningFailure = null;
      nextIndex = 0;
    }

    private void setProvisioningFailure(RuntimeException provisioningFailure) {
      results.clear();
      this.provisioningFailure = provisioningFailure;
      nextIndex = 0;
    }
  }
}
