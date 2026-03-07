package com.fix.channel.entity;

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

  protected Notification() {
  }

  private Notification(Long memberId, String channel, String message, boolean delivered) {
    this.memberId = memberId;
    this.channel = channel;
    this.message = message;
    this.delivered = delivered;
  }

  public static Notification pending(Long memberId, String channel, String message) {
    return new Notification(memberId, channel, message, false);
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

  public void markDelivered() {
    this.delivered = true;
  }
}
