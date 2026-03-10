package com.fix.corebank.dto.request;

import com.fix.corebank.vo.AccountProvisioningCommand;

public class InternalPortfolioProvisioningRequest {

  private Long memberId;
  private String memberNo;
  private String email;

  public AccountProvisioningCommand toVo() {
    return AccountProvisioningCommand.of(memberId, memberNo, email);
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long memberId) {
    this.memberId = memberId;
  }

  public String getMemberNo() {
    return memberNo;
  }

  public void setMemberNo(String memberNo) {
    this.memberNo = memberNo;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}
