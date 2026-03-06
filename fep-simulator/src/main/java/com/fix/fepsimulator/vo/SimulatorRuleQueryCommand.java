package com.fix.fepsimulator.vo;

public class SimulatorRuleQueryCommand {

  private final String ruleCode;

  private SimulatorRuleQueryCommand(String ruleCode) {
    this.ruleCode = ruleCode;
  }

  public static SimulatorRuleQueryCommand of(String ruleCode) {
    return new SimulatorRuleQueryCommand(ruleCode);
  }

  public String getRuleCode() {
    return ruleCode;
  }
}
