package com.fix.fepgateway.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.fepgateway.controlplane.controller.FepGatewayOrderController;
import com.fix.fepgateway.controlplane.service.FepGatewayControlService;
import com.fix.fepgateway.dataplane.fix.FixDataPlaneService;
import com.fix.fepgateway.repository.GatewayOrderCancelRepository;
import com.fix.fepgateway.repository.GatewayOrderReplayRepository;
import com.fix.fepgateway.repository.GatewayOrderRepository;
import com.fix.fepgateway.support.FepGatewayStandaloneMvcSupport;
import com.fix.fepgateway.vo.GatewayOrderResult;
import com.fix.fepgateway.vo.GatewayOrderCancelCommand;
import com.fix.fepgateway.vo.GatewayOrderStatusCommand;
import com.fix.fepgateway.vo.GatewayOrderSubmitCommand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class FepGatewayClOrdIdContractTest {

  private static final String VALID_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174001";
  private static final String OTHER_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174002";
  private static final String CANCEL_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174003";

  private MockMvc mockMvc;
  private StubFepGatewayControlService fepGatewayControlService;

  @BeforeEach
  void setUp() {
    fepGatewayControlService = new StubFepGatewayControlService();
    mockMvc = FepGatewayStandaloneMvcSupport.build(
        List.of(),
        new FepGatewayOrderController(fepGatewayControlService)
    );
  }

  @Test
  void shouldAcceptSubmitWhenHeaderMatchesBodyClOrdId() throws Exception {
    fepGatewayControlService.setSubmitOrderResult(new GatewayOrderResult(
        VALID_CL_ORD_ID,
        "FEP-KRX-" + VALID_CL_ORD_ID,
        FepExecType.FILL,
        FepOrdStatus.FILLED,
        10L,
        72000L,
        0L,
        Instant.parse("2026-03-01T10:00:00Z"),
        null,
        null
    ));

    mockMvc.perform(post("/fep/v1/orders")
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-clord-1001")
            .header(CommonHeaders.X_CL_ORD_ID, VALID_CL_ORD_ID)
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
                  "referenceId": "ref-1001"
                }
                """.formatted(VALID_CL_ORD_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.clOrdId").value(VALID_CL_ORD_ID))
        .andExpect(jsonPath("$.data.ordStatus").value("FILLED"));
  }

  @Test
  void shouldRejectSubmitWhenHeaderDoesNotMatchBodyClOrdId() throws Exception {
    mockMvc.perform(post("/fep/v1/orders")
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-clord-1002")
            .header(CommonHeaders.X_CL_ORD_ID, VALID_CL_ORD_ID)
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
                  "referenceId": "ref-1002"
                }
                """.formatted(OTHER_CL_ORD_ID)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));

    org.assertj.core.api.Assertions.assertThat(fepGatewayControlService.submitOrderCalls()).isZero();
  }

  @Test
  void shouldRejectCancelWhenPathAndBodyOrigClOrdIdDiffer() throws Exception {
    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/cancel", CANCEL_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-clord-2001")
            .content("""
                {
                  "origClOrdId": "%s",
                  "symbol": "005930",
                  "side": "BUY",
                  "cancelQty": 10,
                  "reason": "RECOVERY"
                }
                """.formatted(OTHER_CL_ORD_ID)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));

    org.assertj.core.api.Assertions.assertThat(fepGatewayControlService.submitOrderCalls()).isZero();
  }

  @Test
  void shouldRejectSubmitWhenHeaderIsNotUuidV4() throws Exception {
    mockMvc.perform(post("/fep/v1/orders")
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-clord-1003")
            .header(CommonHeaders.X_CL_ORD_ID, "not-a-uuid")
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
                  "referenceId": "ref-1003"
                }
                """.formatted(VALID_CL_ORD_ID)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));

    org.assertj.core.api.Assertions.assertThat(fepGatewayControlService.submitOrderCalls()).isZero();
  }

  @Test
  void shouldRejectCancelWhenOrigClOrdIdIsNotUuidV4() throws Exception {
    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/cancel", CANCEL_CL_ORD_ID)
            .contentType("application/json")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-clord-2002")
            .content("""
                {
                  "origClOrdId": "legacy-id",
                  "symbol": "005930",
                  "side": "BUY",
                  "cancelQty": 10,
                  "reason": "RECOVERY"
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));

    org.assertj.core.api.Assertions.assertThat(fepGatewayControlService.submitOrderCalls()).isZero();
  }

  private static final class StubFepGatewayControlService extends FepGatewayControlService {

    private GatewayOrderResult submitOrderResult;
    private int submitOrderCalls;

    private StubFepGatewayControlService() {
      super(
          (GatewayOrderRepository) null,
          (GatewayOrderCancelRepository) null,
          (GatewayOrderReplayRepository) null,
          (FixDataPlaneService) null
      );
    }

    @Override
    public GatewayOrderResult submitOrder(GatewayOrderSubmitCommand command) {
      submitOrderCalls++;
      return submitOrderResult;
    }

    @Override
    public GatewayOrderResult status(GatewayOrderStatusCommand command) {
      throw new UnsupportedOperationException();
    }

    @Override
    public com.fix.fepgateway.vo.GatewayCancelResult cancel(GatewayOrderCancelCommand command) {
      throw new UnsupportedOperationException();
    }

    private void setSubmitOrderResult(GatewayOrderResult submitOrderResult) {
      this.submitOrderResult = submitOrderResult;
    }

    private int submitOrderCalls() {
      return submitOrderCalls;
    }
  }
}
