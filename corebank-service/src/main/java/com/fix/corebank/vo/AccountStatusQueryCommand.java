package com.fix.corebank.vo;

public class AccountStatusQueryCommand {

  private final Long accountId;
  private final Long memberId;

  private AccountStatusQueryCommand(Long accountId, Long memberId) {
    this.accountId = accountId;
    this.memberId = memberId;
  }

  public static AccountStatusQueryCommand of(Long accountId, Long memberId) {
    return new AccountStatusQueryCommand(accountId, memberId);
  }

  public Long getAccountId() {
    return accountId;
  }

  public Long getMemberId() {
    return memberId;
  }
}
