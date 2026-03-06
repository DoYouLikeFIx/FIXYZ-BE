package com.fix.fepsimulator.dto.request;

import com.fix.fepsimulator.vo.SimulatorRuleUpsertCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SimulatorRuleUpsertRequest {

  @NotBlank
  private String ruleCode;

  @NotBlank
  private String action;

  @NotNull
  private Boolean enabled;

  public SimulatorRuleUpsertCommand toVo() {
    return SimulatorRuleUpsertCommand.of(ruleCode, action, Boolean.TRUE.equals(enabled));
  }

  public String getRuleCode() {
    return ruleCode;
  }

  public void setRuleCode(String ruleCode) {
    this.ruleCode = ruleCode;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }
}
