package com.fix.fepsimulator.dto.response;

import java.util.List;

public class SimulatorRuleListResponse {

  private final List<SimulatorRuleResponse> activeRules;

  public SimulatorRuleListResponse(List<SimulatorRuleResponse> activeRules) {
    this.activeRules = activeRules;
  }

  public List<SimulatorRuleResponse> getActiveRules() {
    return activeRules;
  }
}