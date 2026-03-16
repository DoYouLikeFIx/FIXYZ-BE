package com.fix.fepsimulator.dto.response;

import com.fix.fepsimulator.vo.SimulatorRuleResult;
import java.time.Instant;

public class SimulatorRuleResponse {

  private final String ruleId;
  private final String action;
  private final String targetSymbol;
  private final String targetExchange;
  private final Long matchAmount;
  private final double probability;
  private final Instant appliedAt;
  private final Instant expiresAt;

  private SimulatorRuleResponse(
      String ruleId,
      String action,
      String targetSymbol,
      String targetExchange,
      Long matchAmount,
      double probability,
      Instant appliedAt,
      Instant expiresAt
  ) {
    this.ruleId = ruleId;
    this.action = action;
    this.targetSymbol = targetSymbol;
    this.targetExchange = targetExchange;
    this.matchAmount = matchAmount;
    this.probability = probability;
    this.appliedAt = appliedAt;
    this.expiresAt = expiresAt;
  }

  public static SimulatorRuleResponse from(SimulatorRuleResult result) {
    return new SimulatorRuleResponse(
        result.getRuleId(),
        result.getAction(),
        result.getTargetSymbol(),
        result.getTargetExchange(),
        result.getMatchAmount(),
        result.getProbability(),
        result.getAppliedAt(),
        result.getExpiresAt()
    );
  }

  public String getRuleId() {
    return ruleId;
  }

  public String getAction() {
    return action;
  }

  public String getTargetSymbol() {
    return targetSymbol;
  }

  public String getTargetExchange() {
    return targetExchange;
  }

  public Long getMatchAmount() {
    return matchAmount;
  }

  public double getProbability() {
    return probability;
  }

  public Instant getAppliedAt() {
    return appliedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
