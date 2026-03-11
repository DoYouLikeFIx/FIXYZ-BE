package com.fix.channel.vo;

import java.time.Instant;

public class OrderSessionResult {

  private final String orderSessionId;
  private final String clOrdId;
  private final String status;
  private final Instant expiresAt;
  private final Long remainingSeconds;
  private final boolean created;

  private OrderSessionResult(
      String orderSessionId,
      String clOrdId,
      String status,
      Instant expiresAt,
      Long remainingSeconds,
      boolean created
  ) {
    this.orderSessionId = orderSessionId;
    this.clOrdId = clOrdId;
    this.status = status;
    this.expiresAt = expiresAt;
    this.remainingSeconds = remainingSeconds;
    this.created = created;
  }

  public static OrderSessionResult of(
      String orderSessionId,
      String clOrdId,
      String status,
      Instant expiresAt,
      Long remainingSeconds,
      boolean created
  ) {
    return new OrderSessionResult(orderSessionId, clOrdId, status, expiresAt, remainingSeconds, created);
  }

  public String getOrderSessionId() {
    return orderSessionId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getStatus() {
    return status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Long getRemainingSeconds() {
    return remainingSeconds;
  }

  public boolean isCreated() {
    return created;
  }
}
