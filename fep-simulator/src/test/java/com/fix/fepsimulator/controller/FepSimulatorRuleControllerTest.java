package com.fix.fepsimulator.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.fepsimulator.repository.SimulatorConnectionRepository;
import com.fix.fepsimulator.repository.SimulatorMessageRepository;
import com.fix.fepsimulator.repository.SimulatorRuleRepository;
import com.fix.fepsimulator.service.FepSimulatorControlService;
import com.fix.fepsimulator.support.FepSimulatorStandaloneMvcSupport;
import com.fix.fepsimulator.vo.SimulatorChaosCommand;
import com.fix.fepsimulator.vo.SimulatorChaosResult;
import com.fix.fepsimulator.vo.SimulatorRuleQueryCommand;
import com.fix.fepsimulator.vo.SimulatorRuleResult;
import com.fix.fepsimulator.vo.SimulatorRuleUpsertCommand;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

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
  void shouldUpsertAndFetchRule() throws Exception {
    fepSimulatorControlService.setUpsertRuleResult(SimulatorRuleResult.of("RULE-LATENCY", "DELAY_100MS", true, true));
    fepSimulatorControlService.setGetRuleResult(SimulatorRuleResult.of("RULE-LATENCY", "DELAY_100MS", true, true));

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
    fepSimulatorControlService.setChaosResult(SimulatorChaosResult.of("SIM-CP-1", "DROP_ACK", "CHAOS_ACCEPTED"));

    mockMvc.perform(post("/simulator/v1/chaos")
            .param("connectionKey", "SIM-CP-1")
            .param("scenario", "DROP_ACK")
            .param("intensity", "35"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("CHAOS_ACCEPTED"));
  }

  private static final class StubFepSimulatorControlService extends FepSimulatorControlService {

    private SimulatorRuleResult upsertRuleResult;
    private SimulatorRuleResult getRuleResult;
    private SimulatorChaosResult chaosResult;

    private StubFepSimulatorControlService() {
      super(
          (SimulatorConnectionRepository) null,
          (SimulatorMessageRepository) null,
          (SimulatorRuleRepository) null
      );
    }

    @Override
    public SimulatorRuleResult upsertRule(SimulatorRuleUpsertCommand command) {
      return upsertRuleResult;
    }

    @Override
    public SimulatorRuleResult getRule(SimulatorRuleQueryCommand command) {
      return getRuleResult;
    }

    @Override
    public SimulatorChaosResult runChaos(SimulatorChaosCommand command) {
      return chaosResult;
    }

    private void setUpsertRuleResult(SimulatorRuleResult upsertRuleResult) {
      this.upsertRuleResult = upsertRuleResult;
    }

    private void setGetRuleResult(SimulatorRuleResult getRuleResult) {
      this.getRuleResult = getRuleResult;
    }

    private void setChaosResult(SimulatorChaosResult chaosResult) {
      this.chaosResult = chaosResult;
    }
  }
}
