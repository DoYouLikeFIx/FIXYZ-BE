package com.fix.fepgateway.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "gateway_sessions")
public class GatewaySession extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, unique = true, length = 128)
  private String sessionId;

  @Column(name = "counterparty", nullable = false, length = 64)
  private String counterparty;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  protected GatewaySession() {
  }

  private GatewaySession(String sessionId, String counterparty, String status) {
    this.sessionId = sessionId;
    this.counterparty = counterparty;
    this.status = status;
  }

  public static GatewaySession connected(String sessionId, String counterparty) {
    return new GatewaySession(sessionId, counterparty, "CONNECTED");
  }

  public Long getId() {
    return id;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getCounterparty() {
    return counterparty;
  }

  public String getStatus() {
    return status;
  }
}
