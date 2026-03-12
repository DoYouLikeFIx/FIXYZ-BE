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
@Table(name = "members")
public class Member extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_no", nullable = false, unique = true, length = 64)
  private String memberNo;

  @Column(name = "email", nullable = false, unique = true, length = 128)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "role", nullable = false, length = 20)
  private String role;

  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "account_number", length = 14)
  private String accountNumber;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "password_changed_at", nullable = false)
  private Instant passwordChangedAt;

  protected Member() {
  }

  private Member(
      String memberNo,
      String email,
      String passwordHash,
      String name,
      String role,
      Long accountId,
      String accountNumber,
      String status,
      int failedLoginAttempts,
      Instant lockedAt,
      Instant passwordChangedAt
  ) {
    this.memberNo = memberNo;
    this.email = email;
    this.passwordHash = passwordHash;
    this.name = name;
    this.role = role;
    this.accountId = accountId;
    this.accountNumber = accountNumber;
    this.status = status;
    this.failedLoginAttempts = failedLoginAttempts;
    this.lockedAt = lockedAt;
    this.passwordChangedAt = passwordChangedAt;
  }

  public static Member registerUser(String memberNo, String email, String passwordHash, String name) {
    Instant now = Instant.now();
    return new Member(memberNo, email, passwordHash, name, "ROLE_USER", null, null, "ACTIVE", 0, null, now);
  }

  // 스캐폴딩 경로 호환을 위해 기존 팩토리를 유지한다.
  // Story 1.1 전환 완료 후 모든 호출은 registerUser(...)로 대체한다.
  public static Member of(String memberNo, String email, String status) {
    return new Member(memberNo, email, "__LEGACY__", memberNo, "ROLE_USER", null, null, status, 0, null, Instant.now());
  }

  public Long getId() {
    return id;
  }

  public String getMemberNo() {
    return memberNo;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getName() {
    return name;
  }

  public String getRole() {
    return role;
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public String getStatus() {
    return status;
  }

  public int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  public Instant getLockedAt() {
    return lockedAt;
  }

  public Instant getPasswordChangedAt() {
    return passwordChangedAt;
  }

  public boolean isLocked() {
    return "LOCKED".equals(status);
  }

  public int increaseFailedLoginAttempts() {
    this.failedLoginAttempts += 1;
    return this.failedLoginAttempts;
  }

  public void resetFailedLoginAttempts() {
    this.failedLoginAttempts = 0;
  }

  public void lock() {
    this.status = "LOCKED";
    this.lockedAt = Instant.now();
  }

  public void activate() {
    this.status = "ACTIVE";
    this.failedLoginAttempts = 0;
    this.lockedAt = null;
  }

  public void updateProfileName(String name) {
    this.name = name;
  }

  public void updateLinkedAccount(Long accountId, String accountNumber) {
    this.accountId = accountId;
    this.accountNumber = accountNumber;
  }

  public void updatePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
    this.passwordChangedAt = Instant.now();
  }
}
