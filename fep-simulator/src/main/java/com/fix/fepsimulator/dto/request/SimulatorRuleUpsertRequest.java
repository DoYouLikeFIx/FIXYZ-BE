package com.fix.fepsimulator.dto.request;

import com.fix.fepsimulator.vo.SimulatorRuleUpsertCommand;

public class SimulatorRuleUpsertRequest {

  private String action;
  private String targetSymbol;
  private String targetExchange;
  private Integer ttlSeconds;
  private Long matchAmount;
  private Double probability;

  public SimulatorRuleUpsertCommand toVo() {
    return SimulatorRuleUpsertCommand.of(action, targetSymbol, targetExchange, ttlSeconds, matchAmount, probability);
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getTargetSymbol() {
    return targetSymbol;
  }

  public void setTargetSymbol(String targetSymbol) {
    this.targetSymbol = targetSymbol;
  }

  public String getTargetExchange() {
    return targetExchange;
  }

  public void setTargetExchange(String targetExchange) {
    this.targetExchange = targetExchange;
  }

  public Integer getTtlSeconds() {
    return ttlSeconds;
  }

  public void setTtlSeconds(Integer ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }

  public Long getMatchAmount() {
    return matchAmount;
  }

  public void setMatchAmount(Long matchAmount) {
    this.matchAmount = matchAmount;
  }

  public Double getProbability() {
    return probability;
  }

  public void setProbability(Double probability) {
    this.probability = probability;
  }
}
