package com.fix.fepsimulator.integration;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.fix.fepsimulator.support.FepSimulatorContainersIntegrationTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;

@SpringBootTest
@AutoConfigureMockMvc
class FepSimulatorContainersIntegrationTest extends FepSimulatorContainersIntegrationTestBase {

  @Autowired
  private MockMvc mockMvc;

  @BeforeEach
  void clearRulesBeforeEach() throws Exception {
    mockMvc.perform(delete("/fep-internal/rules").header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk());
  }

  @Test
  void shouldBootMysqlAndRedisContainers() {
    assertThat(MYSQL_CONTAINER.isRunning()).isTrue();
    assertThat(REDIS_CONTAINER.isRunning()).isTrue();
  }

  @Test
  void shouldResolveWireMockClassesForContractStubCompilation() {
    WireMockServer wireMockServer = wireMockServer();
    wireMockServer.start();
    try {
      assertThat(wireMockServer.baseUrl()).contains("http://localhost");
    } finally {
      wireMockServer.stop();
    }
  }

  @Test
  void shouldListOnlyActiveRulesAndDropExpiredRule() throws Exception {
    mockMvc.perform(put("/fep-internal/rules")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  \"action\": \"APPROVE\",
                  \"targetSymbol\": \"005930\",
                  \"targetExchange\": \"KRX\",
                  \"ttlSeconds\": 1,
                  \"probability\": 1.0
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.action").value("APPROVE"));

    mockMvc.perform(get("/fep-internal/rules").header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeRules.length()").value(1));

    mockMvc.perform(get("/api/v1/ping")
            .param("symbol", "005930")
            .param("exchange", "KRX")
            .param("amount", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chaosAction").value("APPROVE"));

    Thread.sleep(1200L);

    mockMvc.perform(get("/fep-internal/rules").header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeRules.length()").value(0));

    mockMvc.perform(get("/api/v1/ping")
            .param("symbol", "005930")
            .param("exchange", "KRX")
            .param("amount", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chaosAction").value("NONE"));
  }

  @Test
  void shouldClearRulesAndResumeNormalHandling() throws Exception {
    mockMvc.perform(put("/fep-internal/rules")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  \"action\": \"TIMEOUT\",
                  \"targetExchange\": \"KRX\",
                  \"ttlSeconds\": 60,
                  \"probability\": 1.0
                }
                """))
        .andExpect(status().isOk());

    mockMvc.perform(get("/fep-internal/rules").header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeRules.length()").value(1));

    mockMvc.perform(delete("/fep-internal/rules").header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clearedCount").value(1));

    mockMvc.perform(get("/fep-internal/rules").header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeRules.length()").value(0));

    mockMvc.perform(get("/api/v1/ping")
            .param("symbol", "005930")
            .param("exchange", "KRX")
            .param("amount", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chaosAction").value("NONE"));
  }
}
