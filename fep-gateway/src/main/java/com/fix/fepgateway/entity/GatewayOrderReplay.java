package com.fix.fepgateway.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "gateway_order_replays")
public class GatewayOrderReplay extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cl_ord_id", nullable = false, length = 64)
  private String clOrdId;

  @Column(name = "manual_decision", length = 16)
  private String manualDecision;

  @Column(name = "operator_id", length = 64)
  private String operatorId;

  @Column(name = "approved_by", length = 64)
  private String approvedBy;

  @Column(name = "evidence_ref", length = 255)
  private String evidenceRef;

  @Column(name = "reason", nullable = false, length = 255)
  private String reason;

  @Column(name = "execution_price")
  private Long executionPrice;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "execution_source", length = 32)
  private String executionSource;

  @Column(name = "execution_result", length = 32)
  private String executionResult;

  @Column(name = "processed_at")
  private Instant processedAt;

  protected GatewayOrderReplay() {
  }

  private GatewayOrderReplay(
      String clOrdId,
      String manualDecision,
      String operatorId,
      String approvedBy,
      String evidenceRef,
      String reason,
      Long executionPrice,
      String status
  ) {
    this.clOrdId = clOrdId;
    this.manualDecision = manualDecision;
    this.operatorId = operatorId;
    this.approvedBy = approvedBy;
    this.evidenceRef = evidenceRef;
    this.reason = reason;
    this.executionPrice = executionPrice;
    this.status = status;
  }

  public static GatewayOrderReplay requested(
      String clOrdId,
      String manualDecision,
      String operatorId,
      String approvedBy,
      String evidenceRef,
      String reason,
      Long executionPrice
  ) {
    return new GatewayOrderReplay(
        clOrdId,
        manualDecision,
        operatorId,
        approvedBy,
        evidenceRef,
        reason,
        executionPrice,
        "REPLAY_REQUESTED"
    );
  }

  public void complete(String status, String executionSource, String executionResult, Instant processedAt) {
    this.status = status;
    this.executionSource = executionSource;
    this.executionResult = executionResult;
    this.processedAt = processedAt;
  }

  public Long getId() {
    return id;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getManualDecision() {
    return manualDecision;
  }

  public String getOperatorId() {
    return operatorId;
  }

  public String getApprovedBy() {
    return approvedBy;
  }

  public String getEvidenceRef() {
    return evidenceRef;
  }

  public String getReason() {
    return reason;
  }

  public Long getExecutionPrice() {
    return executionPrice;
  }

  public String getStatus() {
    return status;
  }

  public String getExecutionSource() {
    return executionSource;
  }

  public String getExecutionResult() {
    return executionResult;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
