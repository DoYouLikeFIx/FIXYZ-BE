package com.fix.fepsimulator.entity;

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

  @Column(name = "rule_code", nullable = false, unique = true, length = 64)
  private String ruleCode;

  @Column(name = "rule_action", nullable = false, length = 64)
  private String action;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  protected SimulatorRule() {
  }

  private SimulatorRule(String ruleCode, String action, boolean enabled) {
    this.ruleCode = ruleCode;
    this.action = action;
    this.enabled = enabled;
  }

  public static SimulatorRule create(String ruleCode, String action, boolean enabled) {
    return new SimulatorRule(ruleCode, action, enabled);
  }

  public Long getId() {
    return id;
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

  public void update(String action, boolean enabled) {
    this.action = action;
    this.enabled = enabled;
  }
}
