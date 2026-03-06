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

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldAcceptSubmitWhenHeaderMatchesBodyClOrdId() throws Exception {
    mockMvc.perform(post("/fep/v1/orders")
            .header(CommonHeaders.X_CL_ORD_ID, "CL-1001")
            .param("clOrdId", "CL-1001")
            .param("symbol", "AAPL")
            .param("side", "BUY")
            .param("qty", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.clOrdId").value("CL-1001"))
        .andExpect(jsonPath("$.data.plane").value("CONTROL_PLANE_HTTP"));
  }

  @Test
  void shouldRejectSubmitWhenHeaderDoesNotMatchBodyClOrdId() throws Exception {
    mockMvc.perform(post("/fep/v1/orders")
            .header(CommonHeaders.X_CL_ORD_ID, "CL-1002")
            .param("clOrdId", "CL-9999")
            .param("symbol", "AAPL")
            .param("side", "BUY")
            .param("qty", "10"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ORD_001"));
  }

  @Test
  void shouldRejectCancelWhenPathAndBodyClOrdIdDiffer() throws Exception {
    mockMvc.perform(post("/fep/v1/orders/CL-2001/cancel")
            .header(CommonHeaders.X_CL_ORD_ID, "CL-2001")
            .param("clOrdId", "CL-7777")
            .param("reason", "manual cancel"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ORD_001"));
  }
}
