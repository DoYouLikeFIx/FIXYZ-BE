package com.fix.fepgateway.controlplane.controller;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;

final class ClOrdIdHeaderValidator {

  private ClOrdIdHeaderValidator() {
  }

  static void requireExactMatch(String headerValue, String... clOrdIds) {
    if (headerValue == null || headerValue.isBlank()) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "X-ClOrdID header is required");
    }

    for (String clOrdId : clOrdIds) {
      if (clOrdId == null || clOrdId.isBlank()) {
        throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "clOrdId is required");
      }
      if (!headerValue.equals(clOrdId)) {
        throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "X-ClOrdID must match clOrdId");
      }
    }
  }
}
