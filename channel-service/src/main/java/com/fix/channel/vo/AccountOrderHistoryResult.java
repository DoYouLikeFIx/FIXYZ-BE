package com.fix.channel.vo;

import java.util.List;

public class AccountOrderHistoryResult {

  private final List<AccountOrderHistoryItemResult> content;
  private final long totalElements;
  private final int totalPages;
  private final int number;
  private final int size;

  private AccountOrderHistoryResult(
      List<AccountOrderHistoryItemResult> content,
      long totalElements,
      int totalPages,
      int number,
      int size
  ) {
    this.content = content;
    this.totalElements = totalElements;
    this.totalPages = totalPages;
    this.number = number;
    this.size = size;
  }

  public static AccountOrderHistoryResult of(
      List<AccountOrderHistoryItemResult> content,
      long totalElements,
      int totalPages,
      int number,
      int size
  ) {
    return new AccountOrderHistoryResult(content, totalElements, totalPages, number, size);
  }

  public List<AccountOrderHistoryItemResult> getContent() {
    return content;
  }

  public long getTotalElements() {
    return totalElements;
  }

  public int getTotalPages() {
    return totalPages;
  }

  public int getNumber() {
    return number;
  }

  public int getSize() {
    return size;
  }
}
