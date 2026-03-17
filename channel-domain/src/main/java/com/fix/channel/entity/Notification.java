package com.fix.channel.entity;

import java.time.Instant;

import com.fix.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "channel", nullable = false, length = 32)
  private String channel;

  @Column(name = "message", nullable = false, length = 500)
  private String message;

  @Column(name = "delivered", nullable = false)
  private boolean delivered;

  @Column(name = "read_at")
  private Instant readAt;

  protected Notification() {
  }

  private Notification(Long memberId, String channel, String message, boolean delivered, Instant readAt) {
    this.memberId = memberId;
    this.channel = channel;
    this.message = message;
    this.delivered = delivered;
    this.readAt = readAt;
  }

  public static Notification pending(Long memberId, String channel, String message) {
    return new Notification(memberId, channel, message, false, null);
  }

  public Long getId() {
    return id;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getChannel() {
    return channel;
  }

  public String getMessage() {
    return message;
  }

  public boolean isDelivered() {
    return delivered;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public boolean isRead() {
    return readAt != null;
  }

  public void markDelivered() {
    this.delivered = true;
  }

  public void markRead(Instant readAt) {
    if (this.readAt == null) {
      this.readAt = readAt;
    }
  }
}
