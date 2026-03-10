package com.fix.corebank.dto.response;

import com.fix.corebank.vo.AccountPositionResult;
import java.math.BigDecimal;
import java.time.Instant;

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
      Instant asOf
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
        result.getAsOf()
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
}

