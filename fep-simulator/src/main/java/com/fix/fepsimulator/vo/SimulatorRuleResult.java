package com.fix.fepsimulator.vo;

import java.time.Instant;

public class SimulatorRuleResult {

  private final String ruleId;
  private final String action;
  private final String targetSymbol;
  private final String targetExchange;
  private final Long matchAmount;
  private final double probability;
  private final Instant appliedAt;
  private final Instant expiresAt;

  private SimulatorRuleResult(
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

  public static SimulatorRuleResult of(
      String ruleId,
      String action,
      String targetSymbol,
      String targetExchange,
      Long matchAmount,
      double probability,
      Instant appliedAt,
      Instant expiresAt
  ) {
    return new SimulatorRuleResult(ruleId, action, targetSymbol, targetExchange, matchAmount, probability, appliedAt, expiresAt);
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
