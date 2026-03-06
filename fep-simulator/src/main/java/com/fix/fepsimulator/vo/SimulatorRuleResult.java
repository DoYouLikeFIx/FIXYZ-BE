package com.fix.fepsimulator.vo;

public class SimulatorRuleResult {

  private final String ruleCode;
  private final String action;
  private final boolean enabled;
  private final boolean activeInRepository;

  private SimulatorRuleResult(String ruleCode, String action, boolean enabled, boolean activeInRepository) {
    this.ruleCode = ruleCode;
    this.action = action;
    this.enabled = enabled;
    this.activeInRepository = activeInRepository;
  }

  public static SimulatorRuleResult of(String ruleCode, String action, boolean enabled, boolean activeInRepository) {
    return new SimulatorRuleResult(ruleCode, action, enabled, activeInRepository);
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
