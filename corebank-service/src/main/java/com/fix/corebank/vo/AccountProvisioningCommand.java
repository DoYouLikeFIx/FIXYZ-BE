package com.fix.corebank.vo;

public class AccountProvisioningCommand {

  private final Long memberId;
  private final String memberNo;
  private final String email;

  private AccountProvisioningCommand(Long memberId, String memberNo, String email) {
    this.memberId = memberId;
    this.memberNo = memberNo;
    this.email = email;
  }

  public static AccountProvisioningCommand of(Long memberId, String memberNo, String email) {
    return new AccountProvisioningCommand(memberId, memberNo, email);
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
