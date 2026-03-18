package com.fix.corebank.dto.request;

import com.fix.corebank.vo.LedgerReconciliationCaseTransitionCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class InternalLedgerReconciliationCaseTransitionRequest {

  @NotBlank
  @Pattern(
      regexp = "NEW|ACKNOWLEDGED|WAIVED|REPAIR_PENDING|RESOLVED|REOPENED",
      message = "status must be NEW, ACKNOWLEDGED, WAIVED, REPAIR_PENDING, RESOLVED, or REOPENED"
  )
  private String status;

  @NotBlank
  @Size(max = 255)
  private String reason;

  @NotBlank
  @Size(max = 64)
  private String actor;

  @Size(max = 255)
  private String context;

  public LedgerReconciliationCaseTransitionCommand toVo(Long caseId, String correlationId) {
    return LedgerReconciliationCaseTransitionCommand.of(caseId, status, reason, actor, context, correlationId);
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getActor() {
    return actor;
  }

  public void setActor(String actor) {
    this.actor = actor;
  }

  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
  }
}
