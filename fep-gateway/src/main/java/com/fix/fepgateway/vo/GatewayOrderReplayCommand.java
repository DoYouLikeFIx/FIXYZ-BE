package com.fix.fepgateway.vo;

public class GatewayOrderReplayCommand {

  private final String clOrdId;
  private final FepReplayDecision manualDecision;
  private final String operatorId;
  private final String approvedBy;
  private final String evidenceRef;
  private final String reason;
  private final Long executionPrice;

  private GatewayOrderReplayCommand(
      String clOrdId,
      FepReplayDecision manualDecision,
      String operatorId,
      String approvedBy,
      String evidenceRef,
      String reason,
      Long executionPrice
  ) {
    this.clOrdId = clOrdId;
    this.manualDecision = manualDecision;
    this.operatorId = operatorId;
    this.approvedBy = approvedBy;
    this.evidenceRef = evidenceRef;
    this.reason = reason;
    this.executionPrice = executionPrice;
  }

  public static GatewayOrderReplayCommand of(
      String clOrdId,
      FepReplayDecision manualDecision,
      String operatorId,
      String approvedBy,
      String evidenceRef,
      String reason,
      Long executionPrice
  ) {
    return new GatewayOrderReplayCommand(
        clOrdId,
        manualDecision,
        operatorId,
        approvedBy,
        evidenceRef,
        reason,
        executionPrice
    );
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public FepReplayDecision getManualDecision() {
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
}
