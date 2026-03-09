package com.fix.fepgateway.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class FepGatewayInternalSecretFilterTest {

  private static final String FILTER_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174020";

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldBlockInternalRouteWithoutSecret() throws Exception {
    mockMvc.perform(get("/fep-internal/v1/ping"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("Missing or invalid X-Internal-Secret"));
  }

  @Test
  void shouldAllowInternalRouteWithSecret() throws Exception {
    mockMvc.perform(get("/fep-internal/v1/ping").header("X-Internal-Secret", "test-secret"))
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
        .andExpect(jsonPath("$.code").value("AUTH-003"));
  }
}
