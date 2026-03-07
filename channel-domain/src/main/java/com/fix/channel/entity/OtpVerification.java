package com.fix.channel.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "otp_verifications")
public class OtpVerification extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "otp_code", nullable = false, length = 16)
  private String otpCode;

  @Column(name = "verified", nullable = false)
  private boolean verified;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected OtpVerification() {
  }

  private OtpVerification(Long memberId, String otpCode, Instant expiresAt) {
    this.memberId = memberId;
    this.otpCode = otpCode;
    this.expiresAt = expiresAt;
    this.verified = false;
  }

  public static OtpVerification issue(Long memberId, String otpCode, Instant expiresAt) {
    return new OtpVerification(memberId, otpCode, expiresAt);
  }

  public Long getId() {
    return id;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getOtpCode() {
    return otpCode;
  }

  public boolean isVerified() {
    return verified;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void verify() {
    this.verified = true;
  }
}
