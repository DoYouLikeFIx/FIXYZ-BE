package com.fix.channel.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.channel.vo.AccountPositionResult;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.valuation.ValuationStatus;
import com.fix.common.valuation.ValuationUnavailableReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.ALWAYS)
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
    @Schema(nullable = true)
    BigDecimal avgPrice,
    @Schema(nullable = true)
    BigDecimal marketPrice,
    @Schema(nullable = true)
    String quoteSnapshotId,
    @Schema(nullable = true)
    Instant quoteAsOf,
    @Schema(nullable = true)
    FepQuoteSourceMode quoteSourceMode,
    @Schema(nullable = true)
    BigDecimal unrealizedPnl,
    @Schema(nullable = true)
    BigDecimal realizedPnlDaily,
    @NotNull
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
    ValuationStatus valuationStatus,
    @Schema(nullable = true)
    ValuationUnavailableReason valuationUnavailableReason
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
        result.getAvgPrice(),
        result.getMarketPrice(),
        result.getQuoteSnapshotId(),
        result.getQuoteAsOf(),
        result.getQuoteSourceMode(),
        result.getUnrealizedPnl(),
        result.getRealizedPnlDaily(),
        result.getValuationStatus(),
        result.getValuationUnavailableReason()
    );
  }
}
