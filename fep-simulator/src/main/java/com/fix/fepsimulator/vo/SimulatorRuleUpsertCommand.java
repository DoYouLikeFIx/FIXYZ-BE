package com.fix.fepsimulator.vo;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;

public class SimulatorRuleUpsertCommand {

  private static final double DEFAULT_PROBABILITY = 1.0d;

  private final ChaosRuleAction action;
  private final String targetSymbol;
  private final String targetExchange;
  private final int ttlSeconds;
  private final Long matchAmount;
  private final double probability;

  private SimulatorRuleUpsertCommand(
      ChaosRuleAction action,
      String targetSymbol,
      String targetExchange,
      int ttlSeconds,
      Long matchAmount,
      double probability
  ) {
    this.action = action;
    this.targetSymbol = targetSymbol;
    this.targetExchange = targetExchange;
    this.ttlSeconds = ttlSeconds;
    this.matchAmount = matchAmount;
    this.probability = probability;
  }

  public static SimulatorRuleUpsertCommand of(
      String action,
      String targetSymbol,
      String targetExchange,
      Integer ttlSeconds,
      Long matchAmount,
      Double probability
  ) {
    ChaosRuleAction parsedAction = ChaosRuleAction.from(action)
        .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "Unsupported action: " + action));

    if (targetExchange == null || targetExchange.isBlank()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "targetExchange is required");
    }

    if (ttlSeconds == null || ttlSeconds <= 0) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "ttlSeconds must be greater than 0");
    }

    double normalizedProbability = probability == null ? DEFAULT_PROBABILITY : probability;
    if (normalizedProbability < 0.0d || normalizedProbability > 1.0d) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "probability must be within 0.0..1.0");
    }

    return new SimulatorRuleUpsertCommand(
        parsedAction,
        normalizeOptional(targetSymbol),
        targetExchange,
        ttlSeconds,
        matchAmount,
        normalizedProbability
    );
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }

  public ChaosRuleAction getAction() {
    return action;
  }

  public String getTargetSymbol() {
    return targetSymbol;
  }

  public String getTargetExchange() {
    return targetExchange;
  }

  public int getTtlSeconds() {
    return ttlSeconds;
  }

  public Long getMatchAmount() {
    return matchAmount;
  }

  public double getProbability() {
    return probability;
  }
}
