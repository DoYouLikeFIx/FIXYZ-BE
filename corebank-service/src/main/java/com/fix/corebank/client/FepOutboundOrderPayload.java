package com.fix.corebank.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
import com.fix.common.validation.ContractPatterns;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FepOutboundOrderPayload(
    String clOrdId,
    String accountId,
    String symbol,
    FepSecurityExchange securityExchange,
    FepSide side,
    FepOrderType orderType,
    Long qty,
    Long price,
    String quoteSnapshotId,
    Instant quoteAsOf,
    FepQuoteSourceMode quoteSourceMode,
    Long preTradePrice,
    String currency,
    String referenceId
) {

  public FepOutboundOrderPayload {
    require(!isBlank(clOrdId), "clOrdId is required");
    require(ContractPatterns.isUuidV4(clOrdId), "clOrdId must be a UUID v4");
    require(!isBlank(accountId), "accountId is required");
    require(!isBlank(symbol), "symbol is required");
    require(securityExchange != null, "securityExchange is required");
    require(side != null, "side is required");
    require(orderType != null, "orderType is required");
    require(qty != null && qty > 0, "qty is required");
    require(!isBlank(currency), "currency is required");
    require(!isBlank(referenceId), "referenceId is required");

    if (orderType == FepOrderType.LIMIT) {
      require(price != null && price > 0, "price is required for LIMIT orders");
    }

    if (orderType == FepOrderType.MARKET) {
      require(price == null, "price must be omitted for MARKET orders");
      require(!isBlank(quoteSnapshotId), "quoteSnapshotId is required for MARKET orders");
      require(quoteAsOf != null, "quoteAsOf is required for MARKET orders");
      require(quoteSourceMode != null, "quoteSourceMode is required for MARKET orders");
      require(preTradePrice != null && preTradePrice > 0, "preTradePrice is required for MARKET orders");
    }
  }

  private static void require(boolean expression, String message) {
    if (!expression) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, message);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
