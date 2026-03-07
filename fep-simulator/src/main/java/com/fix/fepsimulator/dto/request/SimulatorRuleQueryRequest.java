package com.fix.fepsimulator.dto.request;

import com.fix.fepsimulator.vo.SimulatorRuleQueryCommand;

public class SimulatorRuleQueryRequest {

  public SimulatorRuleQueryCommand toVo(String ruleCode) {
    return SimulatorRuleQueryCommand.of(ruleCode);
  }
}
