package com.fix.corebank.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.valuation.ValuationStatus;
import com.fix.common.valuation.ValuationUnavailableReason;
import com.fix.corebank.vo.AccountPositionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class InternalAccountPositionResponse {

  private final Long accountId;
  private final Long memberId;
  private final String symbol;
  private final BigDecimal quantity;
  private final BigDecimal availableQuantity;
  private final BigDecimal availableQty;
  private final BigDecimal balance;
  private final BigDecimal availableBalance;
  private final String currency;
  private final Instant asOf;
  @Schema(nullable = true)
  private final BigDecimal avgPrice;
  @Schema(nullable = true)
  private final BigDecimal marketPrice;
  @Schema(nullable = true)
  private final String quoteSnapshotId;
  @Schema(nullable = true)
  private final Instant quoteAsOf;
  @Schema(nullable = true)
  private final FepQuoteSourceMode quoteSourceMode;
  @Schema(nullable = true)
  private final BigDecimal unrealizedPnl;
  @Schema(nullable = true)
  private final BigDecimal realizedPnlDaily;
  @NotNull
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = false)
  private final ValuationStatus valuationStatus;
  @Schema(nullable = true)
  private final ValuationUnavailableReason valuationUnavailableReason;

  private InternalAccountPositionResponse(
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
      BigDecimal avgPrice,
      BigDecimal marketPrice,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode,
      BigDecimal unrealizedPnl,
      BigDecimal realizedPnlDaily,
      ValuationStatus valuationStatus,
      ValuationUnavailableReason valuationUnavailableReason
  ) {
    this.accountId = accountId;
    this.memberId = memberId;
    this.symbol = symbol;
    this.quantity = quantity;
    this.availableQuantity = availableQuantity;
    this.availableQty = availableQty;
    this.balance = balance;
    this.availableBalance = availableBalance;
    this.currency = currency;
    this.asOf = asOf;
    this.avgPrice = avgPrice;
    this.marketPrice = marketPrice;
    this.quoteSnapshotId = quoteSnapshotId;
    this.quoteAsOf = quoteAsOf;
    this.quoteSourceMode = quoteSourceMode;
    this.unrealizedPnl = unrealizedPnl;
    this.realizedPnlDaily = realizedPnlDaily;
    this.valuationStatus = valuationStatus;
    this.valuationUnavailableReason = valuationUnavailableReason;
  }

  public static InternalAccountPositionResponse from(AccountPositionResult result) {
    return new InternalAccountPositionResponse(
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

  public Long getAccountId() {
    return accountId;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getSymbol() {
    return symbol;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getAvailableQuantity() {
    return availableQuantity;
  }

  public BigDecimal getAvailableQty() {
    return availableQty;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public BigDecimal getAvailableBalance() {
    return availableBalance;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getAsOf() {
    return asOf;
  }

  public BigDecimal getAvgPrice() {
    return avgPrice;
  }

  public BigDecimal getMarketPrice() {
    return marketPrice;
  }

  public String getQuoteSnapshotId() {
    return quoteSnapshotId;
  }

  public Instant getQuoteAsOf() {
    return quoteAsOf;
  }

  public FepQuoteSourceMode getQuoteSourceMode() {
    return quoteSourceMode;
  }

  public BigDecimal getUnrealizedPnl() {
    return unrealizedPnl;
  }

  public BigDecimal getRealizedPnlDaily() {
    return realizedPnlDaily;
  }

  public ValuationStatus getValuationStatus() {
    return valuationStatus;
  }

  public ValuationUnavailableReason getValuationUnavailableReason() {
    return valuationUnavailableReason;
  }
}
