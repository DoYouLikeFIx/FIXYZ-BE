package com.fix.channel.vo;

public class AccountOrderHistoryQueryCommand {

  private final Long accountId;
  private final Long memberId;
  private final int page;
  private final int size;

  private AccountOrderHistoryQueryCommand(Long accountId, Long memberId, int page, int size) {
    this.accountId = accountId;
    this.memberId = memberId;
    this.page = page;
    this.size = size;
  }

  public static AccountOrderHistoryQueryCommand of(Long accountId, Long memberId, int page, int size) {
    return new AccountOrderHistoryQueryCommand(accountId, memberId, page, size);
  }

  public Long getAccountId() {
    return accountId;
  }

  public Long getMemberId() {
    return memberId;
  }

  public int getPage() {
    return page;
  }

  public int getSize() {
    return size;
  }
}
