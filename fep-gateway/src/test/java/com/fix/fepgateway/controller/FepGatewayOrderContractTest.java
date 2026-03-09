package com.fix.fepgateway.scenario;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.web.CommonHeaders;
import com.fix.fepgateway.entity.GatewayOrder;
import com.fix.fepgateway.repository.GatewayOrderRepository;
import com.fix.fepgateway.vo.GatewayExecutionOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FepGatewayOrderContractTest {

  private static final String SUBMIT_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174010";
  private static final String MARKET_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174011";
  private static final String MISSING_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174012";
  private static final String CANCEL_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174013";
  private static final String INTERNAL_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174014";
  private static final String INTERNAL_PARTIAL_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174015";
  private static final String INTERNAL_PARTIAL_EMPTY_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174016";
  private static final String INTERNAL_MARKET_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174017";
  private static final String CANCEL_TIMEOUT_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174018";
  private static final String CANCEL_REJECT_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174019";
  private static final String CANCEL_PARTIAL_OPEN_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174020";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private GatewayOrderRepository gatewayOrderRepository;

  @Test
  void shouldRejectSubmitWhenRequiredContractFieldsAreMissing() throws Exception {
    mockMvc.perform(post("/fep/v1/orders")
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-001")
            .header(CommonHeaders.X_CL_ORD_ID, SUBMIT_CL_ORD_ID)
            .content("""
                {
                  "clOrdId": "%s",
                  "accountId": "ACC-001",
                  "securityExchange": "KRX",
                  "side": "BUY",
                  "orderType": "LIMIT",
                  "qty": 10,
                  "price": 72000,
                  "currency": "KRW",
                  "referenceId": "ref-contract-001"
                }
                """.formatted(SUBMIT_CL_ORD_ID)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldExposeExplicitExecutionContractForSubmitAndStatus() throws Exception {
    mockMvc.perform(post("/fep/v1/orders")
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-002-submit")
            .header(CommonHeaders.X_CL_ORD_ID, SUBMIT_CL_ORD_ID)
            .content(validSubmitBody(SUBMIT_CL_ORD_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.clOrdId").value(SUBMIT_CL_ORD_ID))
        .andExpect(jsonPath("$.data.fepOrderId").value("FEP-KRX-" + SUBMIT_CL_ORD_ID))
        .andExpect(jsonPath("$.data.execType").value("FILL"))
        .andExpect(jsonPath("$.data.ordStatus").value("FILLED"))
        .andExpect(jsonPath("$.data.executedQty").value(10))
        .andExpect(jsonPath("$.data.executedPrice").value(72000))
        .andExpect(jsonPath("$.data.leavesQty").value(0))
        .andExpect(jsonPath("$.data.transactTime").isNotEmpty());

    mockMvc.perform(get("/fep/v1/orders/{clOrdId}/status", SUBMIT_CL_ORD_ID)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-002-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(SUBMIT_CL_ORD_ID))
        .andExpect(jsonPath("$.data.execType").value("FILL"))
        .andExpect(jsonPath("$.data.ordStatus").value("FILLED"))
        .andExpect(jsonPath("$.data.leavesQty").value(0))
        .andExpect(jsonPath("$.data.queryTime").isNotEmpty());
  }

  @Test
  void shouldRejectMarketSubmitWhenPriceFieldIsProvided() throws Exception {
    mockMvc.perform(post("/fep/v1/orders")
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-003")
            .header(CommonHeaders.X_CL_ORD_ID, MARKET_CL_ORD_ID)
            .content("""
                {
                  "clOrdId": "%s",
                  "accountId": "ACC-001",
                  "symbol": "005930",
                  "securityExchange": "KRX",
                  "side": "BUY",
                  "orderType": "MARKET",
                  "qty": 10,
                  "price": 72000,
                  "quoteSnapshotId": "qsnap-20260301-001122",
                  "quoteAsOf": "2026-03-01T10:00:00Z",
                  "quoteSourceMode": "DELAYED",
                  "preTradePrice": 72000,
                  "currency": "KRW",
                  "referenceId": "ref-contract-003"
                }
                """.formatted(MARKET_CL_ORD_ID)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldReturnUnknownStatusWhenGatewayCannotFindOrder() throws Exception {
    mockMvc.perform(get("/fep/v1/orders/{clOrdId}/status", MISSING_CL_ORD_ID)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-404"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.clOrdId").value(MISSING_CL_ORD_ID))
        .andExpect(jsonPath("$.data.ordStatus").value("UNKNOWN"))
        .andExpect(jsonPath("$.data.message").isNotEmpty())
        .andExpect(jsonPath("$.data.queryTime").isNotEmpty());
  }

  @Test
  void shouldExposeCancelContractForPartialFillCancel() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        CANCEL_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        "FEP-KRX-" + CANCEL_CL_ORD_ID,
        FepExecType.PARTIAL_FILL,
        FepOrdStatus.PARTIALLY_FILLED,
        5L,
        72000L,
        5L,
        Instant.parse("2026-03-01T10:05:45Z")
    ));
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/cancel", CANCEL_CL_ORD_ID)
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-cancel")
            .content("""
                {
                  "origClOrdId": "%s",
                  "symbol": "005930",
                  "side": "BUY",
                  "cancelQty": 5,
                  "reason": "RECOVERY"
                }
                """.formatted(CANCEL_CL_ORD_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.origClOrdId").value(CANCEL_CL_ORD_ID))
        .andExpect(jsonPath("$.data.cancelClOrdId").isNotEmpty())
        .andExpect(jsonPath("$.data.status").value("PARTIAL_FILL_CANCEL"))
        .andExpect(jsonPath("$.data.executedQty").value(5))
        .andExpect(jsonPath("$.data.canceledQty").value(5))
        .andExpect(jsonPath("$.data.executedPrice").value(72000))
        .andExpect(jsonPath("$.data.executedAt").value("2026-03-01T10:05:45Z"))
        .andExpect(jsonPath("$.data.canceledAt").isNotEmpty());
  }

  @Test
  void shouldUpdateInternalStatusWithoutReplayFallback() throws Exception {
    gatewayOrderRepository.save(GatewayOrder.received(
        INTERNAL_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    ));

    mockMvc.perform(post("/fep-internal/v1/orders/{clOrdId}/status", INTERNAL_CL_ORD_ID)
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-internal")
            .header(CommonHeaders.X_CL_ORD_ID, INTERNAL_CL_ORD_ID)
            .content("""
                {
                  "status": "MALFORMED"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(INTERNAL_CL_ORD_ID))
        .andExpect(jsonPath("$.data.execType").value("PENDING_NEW"))
        .andExpect(jsonPath("$.data.ordStatus").value("MALFORMED"))
        .andExpect(jsonPath("$.data.leavesQty").value(10));
  }

  @Test
  void shouldPreserveExecutionDataWhenInternalStatusMovesToMalformed() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        INTERNAL_PARTIAL_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        "FEP-KRX-" + INTERNAL_PARTIAL_CL_ORD_ID,
        FepExecType.PARTIAL_FILL,
        FepOrdStatus.PARTIALLY_FILLED,
        4L,
        72000L,
        6L,
        Instant.parse("2026-03-01T10:05:30Z")
    ));
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep-internal/v1/orders/{clOrdId}/status", INTERNAL_PARTIAL_CL_ORD_ID)
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-internal-preserve")
            .header(CommonHeaders.X_CL_ORD_ID, INTERNAL_PARTIAL_CL_ORD_ID)
            .content("""
                {
                  "status": "MALFORMED"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.execType").value("PARTIAL_FILL"))
        .andExpect(jsonPath("$.data.ordStatus").value("MALFORMED"))
        .andExpect(jsonPath("$.data.executedQty").value(4))
        .andExpect(jsonPath("$.data.executedPrice").value(72000))
        .andExpect(jsonPath("$.data.leavesQty").value(6));
  }

  @Test
  void shouldRejectInternalPartialFillWithoutExecutionQuantity() throws Exception {
    gatewayOrderRepository.save(GatewayOrder.received(
        INTERNAL_PARTIAL_EMPTY_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    ));

    mockMvc.perform(post("/fep-internal/v1/orders/{clOrdId}/status", INTERNAL_PARTIAL_EMPTY_CL_ORD_ID)
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-internal-partial")
            .header(CommonHeaders.X_CL_ORD_ID, INTERNAL_PARTIAL_EMPTY_CL_ORD_ID)
            .content("""
                {
                  "status": "PARTIALLY_FILLED"
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldRequireExecutedPriceForFilledMarketInternalUpdate() throws Exception {
    gatewayOrderRepository.save(GatewayOrder.received(
        INTERNAL_MARKET_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "MARKET",
        null,
        "FIX"
    ));

    mockMvc.perform(post("/fep-internal/v1/orders/{clOrdId}/status", INTERNAL_MARKET_CL_ORD_ID)
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-internal-market")
            .header(CommonHeaders.X_CL_ORD_ID, INTERNAL_MARKET_CL_ORD_ID)
            .content("""
                {
                  "status": "FILLED"
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldExposeCancelTimeoutAndRejectSemantics() throws Exception {
    gatewayOrderRepository.save(GatewayOrder.received(
        CANCEL_TIMEOUT_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    ));
    gatewayOrderRepository.save(GatewayOrder.received(
        CANCEL_REJECT_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    ));

    mockMvc.perform(post("/fep-internal/v1/orders/{clOrdId}/status", CANCEL_TIMEOUT_CL_ORD_ID)
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-cancel-timeout-control")
            .header(CommonHeaders.X_CL_ORD_ID, CANCEL_TIMEOUT_CL_ORD_ID)
            .content("""
                {
                  "status": "PENDING",
                  "cancelFailureMode": "TIMEOUT"
                }
                """))
        .andExpect(status().isOk());

    mockMvc.perform(post("/fep-internal/v1/orders/{clOrdId}/status", CANCEL_REJECT_CL_ORD_ID)
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-cancel-reject-control")
            .header(CommonHeaders.X_CL_ORD_ID, CANCEL_REJECT_CL_ORD_ID)
            .content("""
                {
                  "status": "PENDING",
                  "cancelFailureMode": "REJECT"
                }
                """))
        .andExpect(status().isOk());

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/cancel", CANCEL_TIMEOUT_CL_ORD_ID)
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-cancel-timeout")
            .content("""
                {
                  "origClOrdId": "%s",
                  "symbol": "005930",
                  "side": "BUY",
                  "cancelQty": 10,
                  "reason": "RECOVERY"
                }
                """.formatted(CANCEL_TIMEOUT_CL_ORD_ID)))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("9004"));

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/cancel", CANCEL_REJECT_CL_ORD_ID)
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-cancel-reject")
            .content("""
                {
                  "origClOrdId": "%s",
                  "symbol": "005930",
                  "side": "BUY",
                  "cancelQty": 10,
                  "reason": "RECOVERY"
                }
                """.formatted(CANCEL_REJECT_CL_ORD_ID)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("9006"));
  }

  @Test
  void shouldAllowCancelQuantitySmallerThanRemainingQuantity() throws Exception {
    gatewayOrderRepository.save(GatewayOrder.received(
        CANCEL_PARTIAL_OPEN_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    ));

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/cancel", CANCEL_PARTIAL_OPEN_CL_ORD_ID)
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-cancel-partial-open")
            .content("""
                {
                  "origClOrdId": "%s",
                  "symbol": "005930",
                  "side": "BUY",
                  "cancelQty": 4,
                  "reason": "ADMIN"
                }
                """.formatted(CANCEL_PARTIAL_OPEN_CL_ORD_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELED"))
        .andExpect(jsonPath("$.data.canceledQty").value(4));

    mockMvc.perform(get("/fep/v1/orders/{clOrdId}/status", CANCEL_PARTIAL_OPEN_CL_ORD_ID)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-contract-cancel-partial-open-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ordStatus").value("PENDING"))
        .andExpect(jsonPath("$.data.leavesQty").value(6));
  }

  private String validSubmitBody(String clOrdId) {
    return """
        {
          "clOrdId": "%s",
          "accountId": "ACC-001",
          "symbol": "005930",
          "securityExchange": "KRX",
          "side": "BUY",
          "orderType": "LIMIT",
          "qty": 10,
          "price": 72000,
          "currency": "KRW",
          "referenceId": "ref-contract-002"
        }
        """.formatted(clOrdId);
  }
}
