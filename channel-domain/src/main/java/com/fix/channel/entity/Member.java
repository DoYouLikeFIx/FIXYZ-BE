package com.fix.channel.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

  protected Member() {
  }

  private Member(
      String memberNo,
      String email,
      String passwordHash,
      String name,
      String role,
      String status
  ) {
    this.memberNo = memberNo;
    this.email = email;
    this.passwordHash = passwordHash;
    this.name = name;
    this.role = role;
    this.status = status;
  }

  public static Member registerUser(String memberNo, String email, String passwordHash, String name) {
    return new Member(memberNo, email, passwordHash, name, "ROLE_USER", "ACTIVE");
  }

  // 스캐폴딩 경로 호환을 위해 기존 팩토리를 유지한다.
  // Story 1.1 전환 완료 후 모든 호출은 registerUser(...)로 대체한다.
  public static Member of(String memberNo, String email, String status) {
    return new Member(memberNo, email, "__LEGACY__", memberNo, "ROLE_USER", status);
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

  public void activate() {
    this.status = "ACTIVE";
  }
}
