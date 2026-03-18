package com.fix.channel.vo;

import java.util.List;

public class AdminAuditLogQueryResult {

  private final List<AdminAuditLogItemVo> content;
  private final long totalElements;
  private final int totalPages;
  private final int number;
  private final int size;

  private AdminAuditLogQueryResult(
      List<AdminAuditLogItemVo> content,
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

  public static AdminAuditLogQueryResult of(
      List<AdminAuditLogItemVo> content,
      long totalElements,
      int totalPages,
      int number,
      int size
  ) {
    return new AdminAuditLogQueryResult(content, totalElements, totalPages, number, size);
  }

  public List<AdminAuditLogItemVo> getContent() {
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
