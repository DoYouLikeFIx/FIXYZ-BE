package com.fix.channel.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "password_reset_tokens",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_password_reset_tokens_token_hash", columnNames = "token_hash"),
        @UniqueConstraint(name = "uk_password_reset_tokens_member_active_slot", columnNames = {"member_id", "active_slot"})
    }
)
public class PasswordResetToken extends BaseTimeEntity {

  public static final byte ACTIVE_SLOT = 1;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "token_hash", nullable = false, columnDefinition = "char(64)")
  private String tokenHash;

  @Column(name = "pepper_version", nullable = false, columnDefinition = "smallint")
  private short pepperVersion;

  @Column(name = "active_slot", columnDefinition = "tinyint")
  private Byte activeSlot;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "terminal_reason", length = 16)
  private PasswordResetTokenTerminalReason terminalReason;

  @Column(name = "terminalized_at")
  private Instant terminalizedAt;

  @Column(name = "request_ip", length = 45)
  private String requestIp;

  @Column(name = "request_user_agent_hash", columnDefinition = "char(64)")
  private String requestUserAgentHash;

  protected PasswordResetToken() {
  }

  private PasswordResetToken(
      Long memberId,
      String tokenHash,
      short pepperVersion,
      Byte activeSlot,
      Instant issuedAt,
      Instant expiresAt,
      Instant consumedAt,
      PasswordResetTokenTerminalReason terminalReason,
      Instant terminalizedAt,
      String requestIp,
      String requestUserAgentHash
  ) {
    this.memberId = memberId;
    this.tokenHash = tokenHash;
    this.pepperVersion = pepperVersion;
    this.activeSlot = activeSlot;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
    this.consumedAt = consumedAt;
    this.terminalReason = terminalReason;
    this.terminalizedAt = terminalizedAt;
    this.requestIp = requestIp;
    this.requestUserAgentHash = requestUserAgentHash;
  }

  public static PasswordResetToken issueActive(
      Long memberId,
      String tokenHash,
      short pepperVersion,
      Instant issuedAt,
      Instant expiresAt,
      String requestIp,
      String requestUserAgentHash
  ) {
    return new PasswordResetToken(
        memberId,
        tokenHash,
        pepperVersion,
        ACTIVE_SLOT,
        issuedAt,
        expiresAt,
        null,
        null,
        null,
        requestIp,
        requestUserAgentHash
    );
  }

  public Long getId() {
    return id;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public short getPepperVersion() {
    return pepperVersion;
  }

  public Byte getActiveSlot() {
    return activeSlot;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getConsumedAt() {
    return consumedAt;
  }

  public PasswordResetTokenTerminalReason getTerminalReason() {
    return terminalReason;
  }

  public Instant getTerminalizedAt() {
    return terminalizedAt;
  }

  public String getRequestIp() {
    return requestIp;
  }

  public String getRequestUserAgentHash() {
    return requestUserAgentHash;
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }

  public boolean isActive() {
    return Byte.valueOf(ACTIVE_SLOT).equals(activeSlot);
  }

  public boolean isTerminal() {
    return activeSlot == null && terminalReason != null && terminalizedAt != null;
  }

  public boolean isExpiredAt(Instant referenceTime) {
    return expiresAt.isBefore(referenceTime) || expiresAt.equals(referenceTime);
  }

  public void expireAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public void supersede(Instant terminalizedAt) {
    this.consumedAt = null;
    terminalize(PasswordResetTokenTerminalReason.SUPERSEDED, terminalizedAt);
  }

  public void consume(Instant consumedAt) {
    this.consumedAt = consumedAt;
    terminalize(PasswordResetTokenTerminalReason.CONSUMED, consumedAt);
  }

  public void expire(Instant terminalizedAt) {
    this.consumedAt = null;
    terminalize(PasswordResetTokenTerminalReason.EXPIRED, terminalizedAt);
  }

  private void terminalize(PasswordResetTokenTerminalReason terminalReason, Instant terminalizedAt) {
    this.activeSlot = null;
    this.terminalReason = terminalReason;
    this.terminalizedAt = terminalizedAt;
  }
}
