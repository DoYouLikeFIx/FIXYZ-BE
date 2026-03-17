package com.fix.fepsimulator.entity;

import java.time.Instant;
import java.util.UUID;

import com.fix.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "simulator_rules")
public class SimulatorRule extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "rule_id", nullable = false, unique = true, length = 64)
  private String ruleId;

  @Column(name = "rule_action", nullable = false, length = 64)
  private String action;

  @Column(name = "target_symbol", length = 32)
  private String targetSymbol;

  @Column(name = "target_exchange", nullable = false, length = 32)
  private String targetExchange;

  @Column(name = "ttl_seconds", nullable = false)
  private int ttlSeconds;

  @Column(name = "match_amount")
  private Long matchAmount;

  @Column(name = "probability", nullable = false)
  private double probability;

  @Column(name = "applied_at", nullable = false)
  private Instant appliedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected SimulatorRule() {
  }

  private SimulatorRule(
      String ruleId,
      String action,
      String targetSymbol,
      String targetExchange,
      int ttlSeconds,
      Long matchAmount,
      double probability,
      Instant appliedAt,
      Instant expiresAt
  ) {
    this.ruleId = ruleId;
    this.action = action;
    this.targetSymbol = targetSymbol;
    this.targetExchange = targetExchange;
    this.ttlSeconds = ttlSeconds;
    this.matchAmount = matchAmount;
    this.probability = probability;
    this.appliedAt = appliedAt;
    this.expiresAt = expiresAt;
  }

  public static SimulatorRule create(
      String action,
      String targetSymbol,
      String targetExchange,
      int ttlSeconds,
      Long matchAmount,
      double probability,
      Instant appliedAt
  ) {
    return new SimulatorRule(
        "rule-" + UUID.randomUUID(),
        action,
        targetSymbol,
        targetExchange,
        ttlSeconds,
        matchAmount,
        probability,
        appliedAt,
        appliedAt.plusSeconds(ttlSeconds)
    );
  }

  public boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  public boolean matches(String symbol, String exchange, Long amount) {
    if (exchange == null || !targetExchange.equalsIgnoreCase(exchange)) {
      return false;
    }
    if (targetSymbol != null && !targetSymbol.equalsIgnoreCase(symbol)) {
      return false;
    }
    if (matchAmount != null && !matchAmount.equals(amount)) {
      return false;
    }
    return true;
  }

  public Long getId() {
    return id;
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

  public int getTtlSeconds() {
    return ttlSeconds;
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
