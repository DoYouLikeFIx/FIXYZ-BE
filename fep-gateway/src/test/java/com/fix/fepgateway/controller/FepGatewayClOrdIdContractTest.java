package com.fix.fepgateway.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FepGatewayClOrdIdContractTest {

  private static final String VALID_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174001";
  private static final String OTHER_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174002";
  private static final String CANCEL_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174003";

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldAcceptSubmitWhenHeaderMatchesBodyClOrdId() throws Exception {
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
  }
}
