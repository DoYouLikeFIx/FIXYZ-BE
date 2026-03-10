package com.fix.corebank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "member")
public class MemberEntity {

  @Id
  private Long id;

  @Column(name = "member_no", nullable = false, unique = true, length = 64)
  private String memberNo;

  @Column(name = "email", nullable = false, unique = true, length = 128)
  private String email;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MemberEntity() {
  }

  private MemberEntity(Long id, String memberNo, String email) {
    this.id = id;
    this.memberNo = memberNo;
    this.email = email;
  }

  public static MemberEntity of(Long id, String memberNo, String email) {
    return new MemberEntity(id, memberNo, email);
  }

  public void updateProfile(String memberNo, String email) {
    this.memberNo = memberNo;
    this.email = email;
  }

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
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

  public Instant getCreatedAt() {
    return createdAt;
  }
}
