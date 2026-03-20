package com.fix.corebank.dto.request;

import com.fix.common.validation.ContractPatterns;
import com.fix.corebank.vo.InternalOrderReplayCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public class InternalOrderReplayRequest {

  @NotBlank
  @Pattern(regexp = "APPROVE|REJECT", message = "manualDecision must be APPROVE or REJECT")
  private String manualDecision;

  @NotBlank
  @Pattern(regexp = ContractPatterns.UUID_V4)
  private String operatorId;

  @NotBlank
  @Pattern(regexp = ContractPatterns.UUID_V4)
  private String approvedBy;

  @NotBlank
  private String evidenceRef;

  @NotBlank
  private String reason;

  @Positive
  private Long executionPrice;

  public InternalOrderReplayCommand toVo(String clOrdId, String correlationId) {
    return InternalOrderReplayCommand.of(
        clOrdId,
        manualDecision,
        operatorId,
        approvedBy,
        evidenceRef,
        reason,
        executionPrice,
        correlationId
    );
  }

  public String getManualDecision() {
    return manualDecision;
  }

  public void setManualDecision(String manualDecision) {
    this.manualDecision = manualDecision;
  }

  public String getOperatorId() {
    return operatorId;
  }

  public void setOperatorId(String operatorId) {
    this.operatorId = operatorId;
  }

  public String getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(String approvedBy) {
    this.approvedBy = approvedBy;
  }

  public String getEvidenceRef() {
    return evidenceRef;
  }

  public void setEvidenceRef(String evidenceRef) {
    this.evidenceRef = evidenceRef;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Long getExecutionPrice() {
    return executionPrice;
  }

  public void setExecutionPrice(Long executionPrice) {
    this.executionPrice = executionPrice;
  }
}
