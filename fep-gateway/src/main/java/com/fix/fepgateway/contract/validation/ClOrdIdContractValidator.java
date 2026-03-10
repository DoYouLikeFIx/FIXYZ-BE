package com.fix.fepgateway.contract.validation;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.validation.ContractPatterns;

public final class ClOrdIdContractValidator {

  private ClOrdIdContractValidator() {
  }

  public static void requireExactMatch(String headerValue, String... clOrdIds) {
    if (headerValue == null || headerValue.isBlank()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "X-ClOrdID header is required");
    }
    if (!ContractPatterns.isUuidV4(headerValue)) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "X-ClOrdID must be a UUID v4");
    }

    for (String clOrdId : clOrdIds) {
      if (clOrdId == null || clOrdId.isBlank()) {
        throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "clOrdId is required");
      }
      if (!ContractPatterns.isUuidV4(clOrdId)) {
        throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "clOrdId must be a UUID v4");
      }
      if (!headerValue.equals(clOrdId)) {
        throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "X-ClOrdID must match clOrdId");
      }
    }
  }
}
