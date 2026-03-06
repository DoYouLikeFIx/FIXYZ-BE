package com.fix.fepsimulator.dto.response;

import com.fix.fepsimulator.vo.SimulatorRuleResult;

public class SimulatorRuleResponse {

  private final String ruleCode;
  private final String action;
  private final boolean enabled;
  private final boolean activeInRepository;

  private SimulatorRuleResponse(String ruleCode, String action, boolean enabled, boolean activeInRepository) {
    this.ruleCode = ruleCode;
    this.action = action;
    this.enabled = enabled;
    this.activeInRepository = activeInRepository;
  }

  public static SimulatorRuleResponse from(SimulatorRuleResult result) {
    return new SimulatorRuleResponse(
        result.getRuleCode(),
        result.getAction(),
        result.isEnabled(),
        result.isActiveInRepository()
    );
  }

  public String getRuleCode() {
    return ruleCode;
  }

  public String getAction() {
    return action;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isActiveInRepository() {
    return activeInRepository;
  }
}
