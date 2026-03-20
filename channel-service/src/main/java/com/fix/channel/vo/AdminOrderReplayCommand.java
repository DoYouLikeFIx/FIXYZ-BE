package com.fix.channel.vo;

public class AdminOrderReplayCommand {

  private final String manualDecision;
  private final String approvedBy;
  private final String evidenceRef;
  private final String reason;
  private final Long executionPrice;

  private AdminOrderReplayCommand(
      String manualDecision,
      String approvedBy,
      String evidenceRef,
      String reason,
      Long executionPrice
  ) {
    this.manualDecision = manualDecision;
    this.approvedBy = approvedBy;
    this.evidenceRef = evidenceRef;
    this.reason = reason;
    this.executionPrice = executionPrice;
  }

  public static AdminOrderReplayCommand of(
      String manualDecision,
      String approvedBy,
      String evidenceRef,
      String reason,
      Long executionPrice
  ) {
    return new AdminOrderReplayCommand(
        normalize(manualDecision).toUpperCase(),
        normalize(approvedBy).toLowerCase(),
        normalize(evidenceRef),
        normalize(reason),
        executionPrice
    );
  }

  public String getManualDecision() {
    return manualDecision;
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

  private static String normalize(String value) {
    return value == null ? null : value.trim();
  }
}
