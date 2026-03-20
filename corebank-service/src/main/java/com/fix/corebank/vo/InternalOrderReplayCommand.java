package com.fix.corebank.vo;

public class InternalOrderReplayCommand {

  private final String clOrdId;
  private final String manualDecision;
  private final String operatorId;
  private final String approvedBy;
  private final String evidenceRef;
  private final String reason;
  private final Long executionPrice;
  private final String correlationId;

  private InternalOrderReplayCommand(
      String clOrdId,
      String manualDecision,
      String operatorId,
      String approvedBy,
      String evidenceRef,
      String reason,
      Long executionPrice,
      String correlationId
  ) {
    this.clOrdId = clOrdId;
    this.manualDecision = manualDecision;
    this.operatorId = operatorId;
    this.approvedBy = approvedBy;
    this.evidenceRef = evidenceRef;
    this.reason = reason;
    this.executionPrice = executionPrice;
    this.correlationId = correlationId;
  }

  public static InternalOrderReplayCommand of(
      String clOrdId,
      String manualDecision,
      String operatorId,
      String approvedBy,
      String evidenceRef,
      String reason,
      Long executionPrice,
      String correlationId
  ) {
    return new InternalOrderReplayCommand(
        clOrdId,
        normalize(manualDecision).toUpperCase(),
        normalize(operatorId).toLowerCase(),
        normalize(approvedBy).toLowerCase(),
        normalize(evidenceRef),
        normalize(reason),
        executionPrice,
        correlationId
    );
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

  public String getCorrelationId() {
    return correlationId;
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim();
  }
}
