package com.fix.channel.dto.response;

import com.fix.channel.vo.AccountPositionResult;
import com.fix.common.fep.FepQuoteSourceMode;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountPositionResponse(
    Long accountId,
    Long memberId,
    String symbol,
    BigDecimal quantity,
    BigDecimal availableQuantity,
    BigDecimal availableQty,
    BigDecimal balance,
    BigDecimal availableBalance,
    String currency,
    Instant asOf,
    BigDecimal marketPrice,
    String quoteSnapshotId,
    Instant quoteAsOf,
    FepQuoteSourceMode quoteSourceMode
) {

  public static AccountPositionResponse from(AccountPositionResult result) {
    return new AccountPositionResponse(
        result.getAccountId(),
        result.getMemberId(),
        result.getSymbol(),
        result.getQuantity(),
        result.getAvailableQuantity(),
        result.getAvailableQuantity(),
        result.getBalance(),
        result.getBalance(),
        result.getCurrency(),
        result.getAsOf(),
        result.getMarketPrice(),
        result.getQuoteSnapshotId(),
        result.getQuoteAsOf(),
        result.getQuoteSourceMode()
    );
  }
}
