package com.fix.channel.dto.request;

import com.fix.channel.vo.AdminAccountStatusTransitionCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminAccountStatusTransitionRequest(
    @NotNull Long memberId,
    @NotBlank
    @Pattern(regexp = "ACTIVE|FROZEN|CLOSED", message = "status must be ACTIVE, FROZEN, or CLOSED")
    String status,
    @NotBlank @Size(max = 255) String reason,
    @NotBlank @Size(max = 64) String actor,
    @Size(max = 255) String context
) {

  public AdminAccountStatusTransitionCommand toVo(Long accountId) {
    return AdminAccountStatusTransitionCommand.of(accountId, memberId, status, reason, actor, context);
  }
}
