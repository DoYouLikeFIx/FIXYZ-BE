package com.fix.corebank.dto.request;

import com.fix.corebank.vo.LedgerReconciliationRerunCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InternalLedgerReconciliationRerunRequest {

  @NotBlank
  @Size(max = 255)
  private String reason;

  @NotBlank
  @Size(max = 64)
  private String actor;

  @Size(max = 255)
  private String context;

  public LedgerReconciliationRerunCommand toVo(Long caseId, String correlationId) {
    return LedgerReconciliationRerunCommand.of(caseId, reason, actor, context, correlationId);
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
