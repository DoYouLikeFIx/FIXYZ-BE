package com.fix.corebank.entity;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.util.Locale;

public enum LedgerReconciliationCaseStatus {
  NEW,
  ACKNOWLEDGED,
  WAIVED,
  REPAIR_PENDING,
  RESOLVED,
  REOPENED;

  public static LedgerReconciliationCaseStatus from(String rawStatus) {
    if (rawStatus == null || rawStatus.isBlank()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "reconciliation case targetStatus is required");
    }
    try {
      return LedgerReconciliationCaseStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "unsupported reconciliation case status: " + rawStatus,
          ex
      );
    }
  }

  public boolean isTerminal() {
    return this == WAIVED || this == RESOLVED;
  }
}
