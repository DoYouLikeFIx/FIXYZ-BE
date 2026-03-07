package com.fix.fepgateway.controller;

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
class FepGatewayReplayScenarioTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldReplayAfterSubmitAndExposeReplayedStatus() throws Exception {
    String clOrdId = "CL-REPLAY-001";

    mockMvc.perform(post("/fep/v1/orders")
            .header(CommonHeaders.X_CL_ORD_ID, clOrdId)
            .param("clOrdId", clOrdId)
            .param("symbol", "AAPL")
            .param("side", "BUY")
            .param("qty", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("FIX_ACCEPTED"));

    mockMvc.perform(post("/fep/v1/orders/{clOrdId}/replay", clOrdId)
            .header(CommonHeaders.X_CL_ORD_ID, clOrdId)
            .param("clOrdId", clOrdId)
            .param("reason", "replay-scenario"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("FIX_REPLAY_ACCEPTED"));

    mockMvc.perform(get("/fep/v1/orders/{clOrdId}/status", clOrdId)
            .header(CommonHeaders.X_CL_ORD_ID, clOrdId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(clOrdId))
        .andExpect(jsonPath("$.data.status").value("FIX_REPLAY_ACCEPTED"));
  }
}
