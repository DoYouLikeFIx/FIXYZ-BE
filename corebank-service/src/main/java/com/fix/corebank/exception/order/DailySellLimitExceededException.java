package com.fix.corebank.exception.order;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import java.math.BigDecimal;
import java.util.Map;

public class DailySellLimitExceededException extends BusinessException {

  public DailySellLimitExceededException(
      Long accountId,
      String symbol,
      BigDecimal requestedQty,
      BigDecimal todaySold,
      BigDecimal dailyLimit
  ) {
    super(
        ErrorCode.ORD_DAILY_SELL_LIMIT_EXCEEDED,
        "Daily sell limit exceeded",
        new ErrorMetadata("error.order.daily_sell_limit_exceeded", "DAILY_SELL_LIMIT_EXCEEDED"),
        Map.of(
            "accountId", accountId,
            "symbol", symbol,
            "requestedQty", requestedQty,
            "todaySold", todaySold,
            "dailyLimit", dailyLimit,
            "remainingLimit", dailyLimit.subtract(todaySold).max(BigDecimal.ZERO)
        )
    );
  }
}
