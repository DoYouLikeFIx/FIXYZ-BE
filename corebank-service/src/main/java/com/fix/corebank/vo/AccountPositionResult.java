package com.fix.corebank.vo;

import com.fix.common.fep.FepQuoteSourceMode;
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
  private final BigDecimal marketPrice;
  private final String quoteSnapshotId;
  private final Instant quoteAsOf;
  private final FepQuoteSourceMode quoteSourceMode;

  private AccountPositionResult(
      Long accountId,
      Long memberId,
      String symbol,
      BigDecimal quantity,
      BigDecimal availableQuantity,
      BigDecimal balance,
      String currency,
      Instant asOf,
      BigDecimal marketPrice,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode
  ) {
    this.accountId = accountId;
    this.memberId = memberId;
    this.symbol = symbol;
    this.quantity = quantity;
    this.availableQuantity = availableQuantity;
    this.balance = balance;
    this.currency = currency;
    this.asOf = asOf;
    this.marketPrice = marketPrice;
    this.quoteSnapshotId = quoteSnapshotId;
    this.quoteAsOf = quoteAsOf;
    this.quoteSourceMode = quoteSourceMode;
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
      BigDecimal marketPrice,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode
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
        marketPrice,
        quoteSnapshotId,
        quoteAsOf,
        quoteSourceMode
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
}

