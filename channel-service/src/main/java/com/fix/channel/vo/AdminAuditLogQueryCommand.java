package com.fix.channel.vo;

import java.time.Instant;

public class AdminAuditLogQueryCommand {

  private final int page;
  private final int size;
  private final Instant from;
  private final Instant to;
  private final Long memberId;
  private final String eventType;

  private AdminAuditLogQueryCommand(
      int page,
      int size,
      Instant from,
      Instant to,
      Long memberId,
      String eventType
  ) {
    this.page = page;
    this.size = size;
    this.from = from;
    this.to = to;
    this.memberId = memberId;
    this.eventType = eventType;
  }

  public static AdminAuditLogQueryCommand of(
      int page,
      int size,
      Instant from,
      Instant to,
      Long memberId,
      String eventType
  ) {
    return new AdminAuditLogQueryCommand(page, size, from, to, memberId, eventType);
  }

  public int getPage() {
    return page;
  }

  public int getSize() {
    return size;
  }

  public Instant getFrom() {
    return from;
  }

  public Instant getTo() {
    return to;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getEventType() {
    return eventType;
  }
}
