package com.fix.fepgateway.scenario;

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
class FepGatewayReplayScenarioTest {

  private static final String FILLED_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174130";
  private static final String GOVERNANCE_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174131";
  private static final String MARKET_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174132";
  private static final String UNKNOWN_MARKET_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174133";
  private static final String PARTIAL_FILL_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174134";
  private static final String CANCELED_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174135";
  private static final String CANCELED_PARTIAL_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174136";
  private static final String DEVIATION_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174137";
  private static final String NOT_ESCALATED_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174138";
  private static final String REQUERY_PARTIAL_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174139";
  private static final String REQUERY_REJECTED_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174140";
  private static final String REQUERY_CANCELED_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174141";
  private static final String LIMIT_REFERENCE_MISSING_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174142";
  private static final String OPERATOR_ID = "123e4567-e89b-42d3-a456-426614174101";
  private static final String APPROVER_ID = "123e4567-e89b-42d3-a456-426614174102";
  private static final String LONG_REASON =
      "Manual replay approved after KRX outage with ticket and audit evidence attached.";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private GatewayOrderRepository gatewayOrderRepository;

  @Test
  void shouldReplayFilledOrderAsCompleted() throws Exception {
    mockMvc.perform(post("/fep/v1/orders")
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-submit")
            .header(CommonHeaders.X_CL_ORD_ID, FILLED_CL_ORD_ID)
            .content("""
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
                  "referenceId": "replay-ref-001"
                }
                """.formatted(FILLED_CL_ORD_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ordStatus").value("FILLED"));

    GatewayOrder order = gatewayOrderRepository.findByClOrdId(FILLED_CL_ORD_ID)
        .orElseThrow();
    order.updateRecoveryStatus("ESCALATED");
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", FILLED_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-command")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-001",
                  "reason": "%s"
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(FILLED_CL_ORD_ID))
        .andExpect(jsonPath("$.data.finalStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.executionSource").value("FILLED"))
        .andExpect(jsonPath("$.data.executedQty").value(10))
        .andExpect(jsonPath("$.data.executedPrice").value(72000))
        .andExpect(jsonPath("$.data.processedBy").value(OPERATOR_ID))
        .andExpect(jsonPath("$.data.processedAt").isNotEmpty());
  }

  @Test
  void shouldRejectReplayWhenDualControlApproverMatchesOperator() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        GOVERNANCE_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    );
    order.updateRecoveryStatus("ESCALATED");
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", GOVERNANCE_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-governance")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-002",
                  "reason": "%s"
                }
                """.formatted(OPERATOR_ID, OPERATOR_ID, LONG_REASON)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-004"));
  }

  @Test
  void shouldRequireExecutionPriceForUnresolvedMarketReplayApproval() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        MARKET_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "MARKET",
        null,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        null,
        FepExecType.PENDING_NEW,
        FepOrdStatus.UNKNOWN,
        0L,
        null,
        10L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null
    ));
    order.updateRecoveryStatus("ESCALATED");
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", MARKET_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-price")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-003",
                  "reason": "%s"
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldReplayUnknownMarketOrderAsVirtualFill() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        UNKNOWN_MARKET_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "MARKET",
        70000L,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        null,
        FepExecType.PENDING_NEW,
        FepOrdStatus.UNKNOWN,
        0L,
        null,
        10L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null
    ));
    order.updateRecoveryStatus("ESCALATED");
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", UNKNOWN_MARKET_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-virtual-fill")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-004",
                  "reason": "%s",
                  "executionPrice": 71500
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(UNKNOWN_MARKET_CL_ORD_ID))
        .andExpect(jsonPath("$.data.finalStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.executionSource").value("VIRTUAL_FILL"))
        .andExpect(jsonPath("$.data.executedQty").value(10))
        .andExpect(jsonPath("$.data.executedPrice").value(71500))
        .andExpect(jsonPath("$.data.processedBy").value(OPERATOR_ID))
        .andExpect(jsonPath("$.data.processedAt").isNotEmpty());
  }

  @Test
  void shouldReplayPartiallyFilledOrderAsCompletedUsingFilledSource() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        PARTIAL_FILL_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        "FEP-KRX-" + PARTIAL_FILL_CL_ORD_ID,
        FepExecType.PARTIAL_FILL,
        FepOrdStatus.PARTIALLY_FILLED,
        5L,
        72000L,
        5L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null
    ));
    order.updateRecoveryStatus("ESCALATED");
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", PARTIAL_FILL_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-partial")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-005",
                  "reason": "%s"
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finalStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.executionSource").value("FILLED"))
        .andExpect(jsonPath("$.data.executedQty").value(5))
        .andExpect(jsonPath("$.data.executedPrice").value(72000));
  }

  @Test
  void shouldReplayCanceledOrderAsRaceConditionCanceled() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        CANCELED_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        "FEP-KRX-" + CANCELED_CL_ORD_ID,
        FepExecType.CANCELED,
        FepOrdStatus.CANCELED,
        0L,
        null,
        0L,
        Instant.parse("2026-03-01T10:06:00Z"),
        null,
        null,
        null
    ));
    order.updateRecoveryStatus("ESCALATED");
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", CANCELED_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-canceled")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-006",
                  "reason": "%s"
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finalStatus").value("CANCELED"))
        .andExpect(jsonPath("$.data.executionSource").doesNotExist())
        .andExpect(jsonPath("$.data.executedQty").doesNotExist())
        .andExpect(jsonPath("$.data.executedPrice").doesNotExist());
  }

  @Test
  void shouldReplayCanceledPartialFillAsPartialFillCancel() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        CANCELED_PARTIAL_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        "FEP-KRX-" + CANCELED_PARTIAL_CL_ORD_ID,
        FepExecType.CANCELED,
        FepOrdStatus.CANCELED,
        5L,
        72000L,
        0L,
        Instant.parse("2026-03-01T10:06:00Z"),
        null,
        null,
        null
    ));
    order.updateRecoveryStatus("ESCALATED");
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", CANCELED_PARTIAL_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-canceled-partial")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-007",
                  "reason": "%s"
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finalStatus").value("CANCELED"))
        .andExpect(jsonPath("$.data.executionResult").value("PARTIAL_FILL_CANCEL"))
        .andExpect(jsonPath("$.data.executedQty").value(5))
        .andExpect(jsonPath("$.data.executedPrice").value(72000));
  }

  @Test
  void shouldRejectVirtualFillWhenExecutionPriceExceedsDeviationLimit() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        DEVIATION_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "MARKET",
        70000L,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        null,
        FepExecType.PENDING_NEW,
        FepOrdStatus.UNKNOWN,
        0L,
        null,
        10L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null
    ));
    order.updateRecoveryStatus("ESCALATED");
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", DEVIATION_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-deviation")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-008",
                  "reason": "%s",
                  "executionPrice": 80000
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-002"));
  }

  @Test
  void shouldRejectVirtualFillReplayWhenLimitReferencePriceIsMissing() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        LIMIT_REFERENCE_MISSING_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        null,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        null,
        FepExecType.PENDING_NEW,
        FepOrdStatus.UNKNOWN,
        0L,
        null,
        10L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null
    ));
    order.updateRecoveryStatus("ESCALATED");
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", LIMIT_REFERENCE_MISSING_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-limit-no-reference")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-013",
                  "reason": "%s"
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldRejectReplayWhenOrderIsNotEscalated() throws Exception {
    gatewayOrderRepository.save(GatewayOrder.received(
        NOT_ESCALATED_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    ));

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", NOT_ESCALATED_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-not-escalated")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-009",
                  "reason": "%s"
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("9009"));
  }

  @Test
  void shouldReplayEscalatedUnknownOrderUsingConfiguredRequeryPartialFill() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        REQUERY_PARTIAL_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        null,
        FepExecType.PENDING_NEW,
        FepOrdStatus.UNKNOWN,
        0L,
        null,
        10L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null
    ));
    order.updateRecoveryStatus("ESCALATED");
    order.configureRequeryOutcome(FepOrdStatus.PARTIALLY_FILLED.name(), 4L, 72000L);
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", REQUERY_PARTIAL_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-requery-partial")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-010",
                  "reason": "%s"
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finalStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.executionSource").value("FILLED"))
        .andExpect(jsonPath("$.data.executedQty").value(4))
        .andExpect(jsonPath("$.data.executedPrice").value(72000));
  }

  @Test
  void shouldFailReplayWhenEscalatedRequeryReturnsRejected() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        REQUERY_REJECTED_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        null,
        FepExecType.PENDING_NEW,
        FepOrdStatus.UNKNOWN,
        0L,
        null,
        10L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null
    ));
    order.updateRecoveryStatus("ESCALATED");
    order.configureRequeryOutcome(FepOrdStatus.REJECTED.name(), null, null);
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", REQUERY_REJECTED_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-requery-rejected")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-011",
                  "reason": "%s"
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finalStatus").value("FAILED"))
        .andExpect(jsonPath("$.data.executionSource").doesNotExist())
        .andExpect(jsonPath("$.data.executedQty").doesNotExist())
        .andExpect(jsonPath("$.data.executedPrice").doesNotExist());
  }

  @Test
  void shouldReplayEscalatedUnknownOrderAsPartialFillCancelWhenRequeryShowsCanceled() throws Exception {
    GatewayOrder order = GatewayOrder.received(
        REQUERY_CANCELED_CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    );
    order.applyExecution(new GatewayExecutionOutcome(
        null,
        FepExecType.PENDING_NEW,
        FepOrdStatus.UNKNOWN,
        0L,
        null,
        10L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null
    ));
    order.updateRecoveryStatus("ESCALATED");
    order.configureRequeryOutcome(FepOrdStatus.CANCELED.name(), 5L, 72000L);
    gatewayOrderRepository.save(order);

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", REQUERY_CANCELED_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-replay-requery-canceled")
            .content("""
                {
                  "manualDecision": "APPROVE",
                  "operatorId": "%s",
                  "approvedBy": "%s",
                  "evidenceRef": "OPS-INC-012",
                  "reason": "%s"
                }
                """.formatted(OPERATOR_ID, APPROVER_ID, LONG_REASON)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finalStatus").value("CANCELED"))
        .andExpect(jsonPath("$.data.executionResult").value("PARTIAL_FILL_CANCEL"))
        .andExpect(jsonPath("$.data.executedQty").value(5))
        .andExpect(jsonPath("$.data.executedPrice").value(72000));
  }
}
