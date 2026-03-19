package com.fix.channel.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "manual_recovery_queue_entries",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_manual_recovery_queue_entries_order_session_id",
            columnNames = "order_session_id"
        )
    },
    indexes = {
        @Index(
            name = "idx_manual_recovery_queue_entries_published_at_enqueued_at",
            columnList = "published_at,enqueued_at"
        )
    }
)
public class ManualRecoveryQueueEntry extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_session_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String orderSessionId;

  @Column(name = "cl_ord_id", nullable = false, length = 64)
  private String clOrdId;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "reason", nullable = false, length = 64)
  private String reason;

  @Column(name = "enqueued_at", nullable = false)
  private Instant enqueuedAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  protected ManualRecoveryQueueEntry() {
  }

  private ManualRecoveryQueueEntry(
      String orderSessionId,
      String clOrdId,
      int attemptCount,
      String reason,
      Instant enqueuedAt
  ) {
    this.orderSessionId = orderSessionId;
    this.clOrdId = clOrdId;
    this.attemptCount = attemptCount;
    this.reason = reason;
    this.enqueuedAt = enqueuedAt;
  }

  public static ManualRecoveryQueueEntry pending(
      String orderSessionId,
      String clOrdId,
      int attemptCount,
      String reason,
      Instant enqueuedAt
  ) {
    return new ManualRecoveryQueueEntry(orderSessionId, clOrdId, attemptCount, reason, enqueuedAt);
  }

  public Long getId() {
    return id;
  }

  public String getOrderSessionId() {
    return orderSessionId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public String getReason() {
    return reason;
  }

  public Instant getEnqueuedAt() {
    return enqueuedAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void refresh(int attemptCount, String reason, Instant enqueuedAt) {
    this.attemptCount = attemptCount;
    this.reason = reason;
    this.enqueuedAt = enqueuedAt;
    this.publishedAt = null;
  }

  public void markPublished(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }
}
