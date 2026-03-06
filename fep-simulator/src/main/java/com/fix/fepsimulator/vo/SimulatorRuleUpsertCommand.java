package com.fix.fepsimulator.vo;

public class SimulatorRuleUpsertCommand {

  private final String ruleCode;
  private final String action;
  private final boolean enabled;

  private SimulatorRuleUpsertCommand(String ruleCode, String action, boolean enabled) {
    this.ruleCode = ruleCode;
    this.action = action;
    this.enabled = enabled;
  }

  public static SimulatorRuleUpsertCommand of(String ruleCode, String action, boolean enabled) {
    return new SimulatorRuleUpsertCommand(ruleCode, action, enabled);
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
}
