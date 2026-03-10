package com.fix.fepgateway.dto.request;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.validation.ContractPatterns;
import com.fix.fepgateway.vo.GatewayOrderReplayCommand;
import com.fix.fepgateway.vo.FepReplayDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record FepOrderReplayRequest(
    @NotNull FepReplayDecision manualDecision,
    @NotBlank
    @Pattern(regexp = ContractPatterns.UUID_V4)
    String operatorId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = ContractPatterns.UUID_V4)
    String approvedBy,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    String evidenceRef,
    @NotBlank
    @Schema(minLength = ContractPatterns.REPLAY_REASON_MIN_LENGTH)
    String reason,
    @Schema(description = "Required for unresolved MARKET replay approval. Values outside maxVirtualFillDeviationBps fail with VALIDATION-002.")
    @Positive Long executionPrice
) {

  public GatewayOrderReplayCommand toVo(String pathClOrdId) {
    if (approvedBy == null || approvedBy.isBlank()) {
      throw new BusinessException(
          ErrorCode.MANUAL_REPLAY_GOVERNANCE_FAILED,
          "approvedBy is required"
      );
    }
    if (!ContractPatterns.isUuidV4(approvedBy)) {
      throw new BusinessException(
          ErrorCode.MANUAL_REPLAY_GOVERNANCE_FAILED,
          "approvedBy must be a UUID v4"
      );
    }
    if (operatorId.equals(approvedBy)) {
      throw new BusinessException(
          ErrorCode.MANUAL_REPLAY_GOVERNANCE_FAILED,
          "approvedBy must differ from operatorId"
      );
    }
    if (evidenceRef == null || evidenceRef.isBlank()) {
      throw new BusinessException(
          ErrorCode.MANUAL_REPLAY_GOVERNANCE_FAILED,
          "evidenceRef is required"
      );
    }
    if (reason.length() < ContractPatterns.REPLAY_REASON_MIN_LENGTH) {
      throw new BusinessException(
          ErrorCode.MANUAL_REPLAY_GOVERNANCE_FAILED,
          "reason must be at least %d characters".formatted(ContractPatterns.REPLAY_REASON_MIN_LENGTH)
      );
    }
    return GatewayOrderReplayCommand.of(
        pathClOrdId,
        manualDecision,
        operatorId,
        approvedBy,
        evidenceRef,
        reason,
        executionPrice
    );
  }
}
