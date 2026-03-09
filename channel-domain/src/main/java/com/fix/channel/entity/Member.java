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

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_at")
  private Instant lockedAt;

  protected Member() {
  }

  private Member(
      String memberNo,
      String email,
      String passwordHash,
      String name,
      String role,
      String status,
      int failedLoginAttempts,
      Instant lockedAt
  ) {
    this.memberNo = memberNo;
    this.email = email;
    this.passwordHash = passwordHash;
    this.name = name;
    this.role = role;
    this.status = status;
    this.failedLoginAttempts = failedLoginAttempts;
    this.lockedAt = lockedAt;
  }

  public static Member registerUser(String memberNo, String email, String passwordHash, String name) {
    return new Member(memberNo, email, passwordHash, name, "ROLE_USER", "ACTIVE", 0, null);
  }

  // 스캐폴딩 경로 호환을 위해 기존 팩토리를 유지한다.
  // Story 1.1 전환 완료 후 모든 호출은 registerUser(...)로 대체한다.
  public static Member of(String memberNo, String email, String status) {
    return new Member(memberNo, email, "__LEGACY__", memberNo, "ROLE_USER", status, 0, null);
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

  public String getStatus() {
    return status;
  }

  public int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  public Instant getLockedAt() {
    return lockedAt;
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

  public void updatePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }
}
