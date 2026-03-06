package com.fix.fepsimulator.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FepSimulatorRuleControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldUpsertAndFetchRule() throws Exception {
    mockMvc.perform(post("/simulator/v1/rules")
            .param("ruleCode", "RULE-LATENCY")
            .param("action", "DELAY_100MS")
            .param("enabled", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.ruleCode").value("RULE-LATENCY"))
        .andExpect(jsonPath("$.data.enabled").value(true));

    mockMvc.perform(get("/simulator/v1/rules/RULE-LATENCY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.ruleCode").value("RULE-LATENCY"));
  }

  @Test
  void shouldAcceptChaosRequest() throws Exception {
    mockMvc.perform(post("/simulator/v1/chaos")
            .param("connectionKey", "SIM-CP-1")
            .param("scenario", "DROP_ACK")
            .param("intensity", "35"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("CHAOS_ACCEPTED"));
  }
}
