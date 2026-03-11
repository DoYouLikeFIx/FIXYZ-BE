package com.fix.corebank.vo;

public class AccountPositionsQueryCommand {

  private final Long accountId;
  private final Long memberId;

  private AccountPositionsQueryCommand(Long accountId, Long memberId) {
    this.accountId = accountId;
    this.memberId = memberId;
  }

  public static AccountPositionsQueryCommand of(Long accountId, Long memberId) {
    return new AccountPositionsQueryCommand(accountId, memberId);
  }

  public Long getAccountId() {
    return accountId;
  }

  public Long getMemberId() {
    return memberId;
  }
}
