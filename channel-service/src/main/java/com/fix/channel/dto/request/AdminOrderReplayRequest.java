package com.fix.channel.dto.request;

import com.fix.channel.vo.AdminOrderReplayCommand;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.validation.ContractPatterns;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminOrderReplayRequest(
    @Schema(allowableValues = {"APPROVE", "REJECT"})
    String manualDecision,
    String approvedBy,
    String evidenceRef,
    String reason,
    @Schema(description = "Manual execution price for MARKET virtual fills")
    Long executionPrice
) {

  public AdminOrderReplayCommand toVo() {
    String normalizedManualDecision = normalize(manualDecision);
    String normalizedApprovedBy = normalize(approvedBy);
    String normalizedEvidenceRef = normalize(evidenceRef);
    String normalizedReason = normalize(reason);

    if (normalizedManualDecision == null || normalizedManualDecision.isBlank()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "manualDecision is required");
    }
    if (!"APPROVE".equals(normalizedManualDecision) && !"REJECT".equals(normalizedManualDecision)) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "manualDecision must be APPROVE or REJECT"
      );
    }
    if (normalizedApprovedBy == null || normalizedApprovedBy.isBlank()) {
      throw new BusinessException(ErrorCode.MANUAL_REPLAY_GOVERNANCE_FAILED, "approvedBy is required");
    }
    if (!ContractPatterns.isUuidV4(normalizedApprovedBy)) {
      throw new BusinessException(ErrorCode.MANUAL_REPLAY_GOVERNANCE_FAILED, "approvedBy must be a UUID v4");
    }
    if (normalizedEvidenceRef == null || normalizedEvidenceRef.isBlank()) {
      throw new BusinessException(ErrorCode.MANUAL_REPLAY_GOVERNANCE_FAILED, "evidenceRef is required");
    }
    if (normalizedReason == null || normalizedReason.length() < ContractPatterns.REPLAY_REASON_MIN_LENGTH) {
      throw new BusinessException(
          ErrorCode.MANUAL_REPLAY_GOVERNANCE_FAILED,
          "reason must be at least %d characters".formatted(ContractPatterns.REPLAY_REASON_MIN_LENGTH)
      );
    }
    if (executionPrice != null && executionPrice <= 0) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "executionPrice must be greater than zero");
    }

    return AdminOrderReplayCommand.of(
        normalizedManualDecision,
        normalizedApprovedBy,
        normalizedEvidenceRef,
        normalizedReason,
        executionPrice
    );
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim();
  }
}
