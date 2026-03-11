package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_status_events")
public class AccountStatusEvent extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "previous_status", nullable = false, length = 16)
  private String previousStatus;

  @Column(name = "new_status", nullable = false, length = 16)
  private String newStatus;

  @Column(name = "reason", nullable = false, length = 255)
  private String reason;

  @Column(name = "actor", nullable = false, length = 64)
  private String actor;

  @Column(name = "context", length = 255)
  private String context;

  @Column(name = "correlation_id", length = 64)
  private String correlationId;

  protected AccountStatusEvent() {
  }

  private AccountStatusEvent(
      Long accountId,
      Long memberId,
      String previousStatus,
      String newStatus,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    this.accountId = accountId;
    this.memberId = memberId;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.reason = reason;
    this.actor = actor;
    this.context = context;
    this.correlationId = correlationId;
  }

  public static AccountStatusEvent of(
      Long accountId,
      Long memberId,
      String previousStatus,
      String newStatus,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    return new AccountStatusEvent(accountId, memberId, previousStatus, newStatus, reason, actor, context, correlationId);
  }

  public Long getId() {
    return id;
  }

  public Long getAccountId() {
    return accountId;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getPreviousStatus() {
    return previousStatus;
  }

  public String getNewStatus() {
    return newStatus;
  }

  public String getReason() {
    return reason;
  }

  public String getActor() {
    return actor;
  }

  public String getContext() {
    return context;
  }

  public String getCorrelationId() {
    return correlationId;
  }
}
