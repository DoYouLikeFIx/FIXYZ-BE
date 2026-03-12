package com.fix.channel.vo;

public class AccountSummaryQueryCommand {

  private final Long accountId;
  private final Long memberId;

  private AccountSummaryQueryCommand(Long accountId, Long memberId) {
    this.accountId = accountId;
    this.memberId = memberId;
  }

  public static AccountSummaryQueryCommand of(Long accountId, Long memberId) {
    return new AccountSummaryQueryCommand(accountId, memberId);
  }

  public Long getAccountId() {
    return accountId;
  }

  public Long getMemberId() {
    return memberId;
  }
}
