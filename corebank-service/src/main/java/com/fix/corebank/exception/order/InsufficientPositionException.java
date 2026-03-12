package com.fix.corebank.exception.order;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import java.math.BigDecimal;
import java.util.Map;

public class InsufficientPositionException extends BusinessException {

  public InsufficientPositionException(
      Long accountId,
      String symbol,
      BigDecimal availableQty,
      BigDecimal requestedQty
  ) {
    super(
        ErrorCode.ORD_INSUFFICIENT_POSITION,
        "Insufficient position quantity",
        new ErrorMetadata("error.order.insufficient_position", "INSUFFICIENT_POSITION"),
        Map.of(
            "accountId", accountId,
            "symbol", symbol,
            "availableQty", availableQty,
            "requestedQty", requestedQty
        )
    );
  }
}
