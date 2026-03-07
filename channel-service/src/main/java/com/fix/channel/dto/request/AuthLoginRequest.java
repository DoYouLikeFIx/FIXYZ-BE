package com.fix.channel.dto.request;

import com.fix.channel.vo.AuthLoginCommand;
import jakarta.validation.constraints.NotBlank;

public class AuthLoginRequest {

  @NotBlank
  private String memberNo;

  @NotBlank
  private String password;

  public AuthLoginCommand toVo() {
    return AuthLoginCommand.of(memberNo, password);
  }

  public String getMemberNo() {
    return memberNo;
  }

  public void setMemberNo(String memberNo) {
    this.memberNo = memberNo;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
