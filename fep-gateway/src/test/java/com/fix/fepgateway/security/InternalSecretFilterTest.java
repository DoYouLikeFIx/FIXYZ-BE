package com.fix.fepgateway.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fix.common.web.CommonHeaders;
import com.fix.fepgateway.controller.FepGatewayController;
import com.fix.fepgateway.filter.CorrelationIdFilter;
import com.fix.fepgateway.support.FepGatewayStandaloneMvcSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class InternalSecretFilterTest {

  private static final String FILTER_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174020";

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = FepGatewayStandaloneMvcSupport.build(
        List.of(
            new CorrelationIdFilter(),
            new InternalSecretFilter("test-secret", JsonMapper.builder().findAndAddModules().build())
        ),
        new FepGatewayController()
    );
  }

  @Test
  void shouldBlockInternalRouteWithoutSecret() throws Exception {
    mockMvc.perform(get("/fep-internal/v1/ping"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists(CommonHeaders.X_CORRELATION_ID))
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("Missing or invalid X-Internal-Secret"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void shouldAllowInternalRouteWithSecret() throws Exception {
    mockMvc.perform(get("/fep-internal/v1/ping").header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk());
  }

  @Test
  void shouldBlockFepContractRouteWithoutSecret() throws Exception {
    mockMvc.perform(post("/fep/v1/orders")
            .contentType("application/json")
            .header(CommonHeaders.X_CL_ORD_ID, FILTER_CL_ORD_ID)
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
                  "referenceId": "ref-filter-001"
                }
                """.formatted(FILTER_CL_ORD_ID)))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists(CommonHeaders.X_CORRELATION_ID))
        .andExpect(jsonPath("$.code").value("AUTH-003"));
  }

  @Test
  void shouldPreserveProvidedCorrelationIdForUnauthorizedRequest() throws Exception {
    mockMvc.perform(get("/fep-internal/v1/ping").header(CommonHeaders.X_CORRELATION_ID, "corr-gateway-unauthorized"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-gateway-unauthorized"))
        .andExpect(jsonPath("$.correlationId").value("corr-gateway-unauthorized"));
  }
}
