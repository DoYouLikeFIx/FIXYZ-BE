package com.fix.fepsimulator.repository.custom;

public interface SimulatorRuleCustomRepository {
  boolean existsEnabledRuleByRuleCode(String ruleCode);
}
