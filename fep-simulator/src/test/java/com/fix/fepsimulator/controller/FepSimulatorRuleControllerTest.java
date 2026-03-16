package com.fix.fepsimulator.controller;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.fepsimulator.repository.SimulatorRuleRepository;
import com.fix.fepsimulator.service.FepSimulatorControlService;
import com.fix.fepsimulator.support.FepSimulatorStandaloneMvcSupport;
import com.fix.fepsimulator.vo.SimulatorRuleResult;
import com.fix.fepsimulator.vo.SimulatorRuleUpsertCommand;

@ExtendWith(MockitoExtension.class)
class FepSimulatorRuleControllerTest {

  private MockMvc mockMvc;
  private StubFepSimulatorControlService fepSimulatorControlService;

  @BeforeEach
  void setUp() {
    fepSimulatorControlService = new StubFepSimulatorControlService();
    mockMvc = FepSimulatorStandaloneMvcSupport.build(
        List.of(),
        new FepSimulatorRuleController(fepSimulatorControlService)
    );
  }

  @Test
  void shouldApplyRuleUsingCanonicalEndpoint() throws Exception {
    fepSimulatorControlService.setUpsertRuleResult(SimulatorRuleResult.of(
        "rule-1",
        "TIMEOUT",
        "005930",
        "KRX",
        null,
        1.0d,
        Instant.parse("2026-03-01T10:00:00Z"),
        Instant.parse("2026-03-01T10:01:00Z")
    ));

    mockMvc.perform(put("/fep-internal/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  \"action\": \"TIMEOUT\",
                  \"targetSymbol\": \"005930\",
                  \"targetExchange\": \"KRX\",
                  \"ttlSeconds\": 60,
                  \"probability\": 1.0
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.ruleId").value("rule-1"))
        .andExpect(jsonPath("$.data.action").value("TIMEOUT"))
        .andExpect(jsonPath("$.data.targetExchange").value("KRX"));
  }

  @Test
  void shouldReturnActiveRules() throws Exception {
    fepSimulatorControlService.setActiveRules(List.of(
        SimulatorRuleResult.of(
            "rule-1",
            "APPROVE",
            "005930",
            "KRX",
            null,
            1.0d,
            Instant.parse("2026-03-01T10:00:00Z"),
            Instant.parse("2026-03-01T10:01:00Z")
        )
    ));

    mockMvc.perform(get("/fep-internal/rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.activeRules[0].ruleId").value("rule-1"));
  }

  @Test
  void shouldClearRules() throws Exception {
    fepSimulatorControlService.setClearCount(2);

    mockMvc.perform(delete("/fep-internal/rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.clearedCount").value(2));
  }

  @Test
  void shouldRejectInvalidAction() throws Exception {
    mockMvc.perform(put("/fep-internal/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  \"action\": \"UNKNOWN\",
                  \"targetExchange\": \"KRX\",
                  \"ttlSeconds\": 60
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldRejectOutOfRangeProbability() throws Exception {
    mockMvc.perform(put("/fep-internal/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  \"action\": \"TIMEOUT\",
                  \"targetExchange\": \"KRX\",
                  \"ttlSeconds\": 60,
                  \"probability\": 1.5
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldRejectNonPositiveTtlSeconds() throws Exception {
    mockMvc.perform(put("/fep-internal/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  \"action\": \"TIMEOUT\",
                  \"targetExchange\": \"KRX\",
                  \"ttlSeconds\": 0
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  private static final class StubFepSimulatorControlService extends FepSimulatorControlService {

    private SimulatorRuleResult upsertRuleResult;
    private List<SimulatorRuleResult> activeRules = List.of();
    private int clearCount;

    private StubFepSimulatorControlService() {
      super((SimulatorRuleRepository) null);
    }

    @Override
    public SimulatorRuleResult applyRule(SimulatorRuleUpsertCommand command, String requestUri, String requestSource) {
      return upsertRuleResult;
    }

    @Override
    public List<SimulatorRuleResult> listActiveRules() {
      return activeRules;
    }

    @Override
    public int clearRules(String requestUri, String requestSource) {
      return clearCount;
    }

    private void setUpsertRuleResult(SimulatorRuleResult upsertRuleResult) {
      this.upsertRuleResult = upsertRuleResult;
    }

    private void setActiveRules(List<SimulatorRuleResult> activeRules) {
      this.activeRules = activeRules;
    }

    private void setClearCount(int clearCount) {
      this.clearCount = clearCount;
    }
  }
}
