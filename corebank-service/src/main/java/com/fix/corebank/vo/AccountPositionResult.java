package com.fix.corebank.vo;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.valuation.ValuationStatus;
import com.fix.common.valuation.ValuationUnavailableReason;
import java.math.BigDecimal;
import java.time.Instant;

public class AccountPositionResult {

  private final Long accountId;
  private final Long memberId;
  private final String symbol;
  private final BigDecimal quantity;
  private final BigDecimal availableQuantity;
  private final BigDecimal balance;
  private final String currency;
  private final Instant asOf;
  private final BigDecimal avgPrice;
  private final BigDecimal marketPrice;
  private final String quoteSnapshotId;
  private final Instant quoteAsOf;
  private final FepQuoteSourceMode quoteSourceMode;
  private final BigDecimal unrealizedPnl;
  private final BigDecimal realizedPnlDaily;
  private final ValuationStatus valuationStatus;
  private final ValuationUnavailableReason valuationUnavailableReason;

  private AccountPositionResult(
      Long accountId,
      Long memberId,
      String symbol,
      BigDecimal quantity,
      BigDecimal availableQuantity,
      BigDecimal balance,
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
    this.balance = balance;
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

  public static AccountPositionResult of(
      Long accountId,
      Long memberId,
      String symbol,
      BigDecimal quantity,
      BigDecimal availableQuantity,
      BigDecimal balance,
      String currency,
      Instant asOf
  ) {
    return new AccountPositionResult(
        accountId,
        memberId,
        symbol,
        quantity,
        availableQuantity,
        balance,
        currency,
        asOf,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null
    );
  }

  public static AccountPositionResult of(
      Long accountId,
      Long memberId,
      String symbol,
      BigDecimal quantity,
      BigDecimal availableQuantity,
      BigDecimal balance,
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
    return new AccountPositionResult(
        accountId,
        memberId,
        symbol,
        quantity,
        availableQuantity,
        balance,
        currency,
        asOf,
        avgPrice,
        marketPrice,
        quoteSnapshotId,
        quoteAsOf,
        quoteSourceMode,
        unrealizedPnl,
        realizedPnlDaily,
        valuationStatus,
        valuationUnavailableReason
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

  public BigDecimal getBalance() {
    return balance;
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
