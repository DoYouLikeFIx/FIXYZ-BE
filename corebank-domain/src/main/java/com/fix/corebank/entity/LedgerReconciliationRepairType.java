package com.fix.corebank.entity;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.util.Locale;

public enum LedgerReconciliationRepairType {
  REBUILD_POSITION_FROM_EXECUTIONS,
  ATTACH_LEDGER_CL_ORD_REF,
  MARK_FALSE_POSITIVE;

  public static LedgerReconciliationRepairType from(String rawType) {
    if (rawType == null || rawType.isBlank()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "repairType is required");
    }
    try {
      return LedgerReconciliationRepairType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "unsupported repair type: " + rawType, ex);
    }
  }
}
