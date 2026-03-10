package com.fix.channel.client;

public class CorebankProvisioningRequest {

  private final Long memberId;
  private final String memberNo;
  private final String email;

  CorebankProvisioningRequest(Long memberId, String memberNo, String email) {
    this.memberId = memberId;
    this.memberNo = memberNo;
    this.email = email;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getMemberNo() {
    return memberNo;
  }

  public String getEmail() {
    return email;
  }
}
