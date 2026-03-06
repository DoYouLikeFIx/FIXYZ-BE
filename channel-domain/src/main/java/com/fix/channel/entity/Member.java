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

  @Column(name = "email", nullable = false, length = 128)
  private String email;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  protected Member() {
  }

  private Member(String memberNo, String email, String status) {
    this.memberNo = memberNo;
    this.email = email;
    this.status = status;
  }

  public static Member of(String memberNo, String email, String status) {
    return new Member(memberNo, email, status);
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

  public String getStatus() {
    return status;
  }

  public void activate() {
    this.status = "ACTIVE";
  }
}
