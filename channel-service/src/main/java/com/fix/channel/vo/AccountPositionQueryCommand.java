package com.fix.channel.vo;

public class AccountPositionQueryCommand {

  private final Long accountId;
  private final Long memberId;
  private final String symbol;

  private AccountPositionQueryCommand(Long accountId, Long memberId, String symbol) {
    this.accountId = accountId;
    this.memberId = memberId;
    this.symbol = symbol;
  }

  public static AccountPositionQueryCommand of(Long accountId, Long memberId, String symbol) {
    return new AccountPositionQueryCommand(accountId, memberId, symbol);
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
}
