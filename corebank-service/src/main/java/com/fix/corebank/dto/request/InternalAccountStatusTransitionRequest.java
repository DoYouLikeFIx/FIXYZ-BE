package com.fix.corebank.dto.request;

import com.fix.corebank.vo.AccountStatusTransitionCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class InternalAccountStatusTransitionRequest {

  @NotNull
  private Long memberId;

  @NotBlank
  @Pattern(regexp = "ACTIVE|FROZEN|CLOSED", message = "status must be ACTIVE, FROZEN, or CLOSED")
  private String status;

  @NotBlank
  @Size(max = 255)
  private String reason;

  @NotBlank
  @Size(max = 64)
  private String actor;

  @Size(max = 255)
  private String context;

  public AccountStatusTransitionCommand toVo(Long accountId, String correlationId) {
    return AccountStatusTransitionCommand.of(
        accountId,
        memberId,
        status,
        reason,
        actor,
        context,
        correlationId
    );
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long memberId) {
    this.memberId = memberId;
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
