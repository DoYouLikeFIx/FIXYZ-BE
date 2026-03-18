package com.fix.corebank.dto.request;

import com.fix.corebank.vo.LedgerReconciliationRepairCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class InternalLedgerReconciliationRepairRequest {

  @NotBlank
  @Size(max = 64)
  private String repairKey;

  @NotBlank
  @Pattern(
      regexp = "REBUILD_POSITION_FROM_EXECUTIONS|ATTACH_LEDGER_CL_ORD_REF|MARK_FALSE_POSITIVE",
      message = "repairType must be REBUILD_POSITION_FROM_EXECUTIONS, ATTACH_LEDGER_CL_ORD_REF, or MARK_FALSE_POSITIVE"
  )
  private String repairType;

  @NotBlank
  @Size(max = 255)
  private String reason;

  @NotBlank
  @Size(max = 64)
  private String actor;

  @Size(max = 255)
  private String context;

  public LedgerReconciliationRepairCommand toVo(Long caseId, String correlationId) {
    return LedgerReconciliationRepairCommand.of(caseId, repairKey, repairType, reason, actor, context, correlationId);
  }

  public String getRepairKey() {
    return repairKey;
  }

  public void setRepairKey(String repairKey) {
    this.repairKey = repairKey;
  }

  public String getRepairType() {
    return repairType;
  }

  public void setRepairType(String repairType) {
    this.repairType = repairType;
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
