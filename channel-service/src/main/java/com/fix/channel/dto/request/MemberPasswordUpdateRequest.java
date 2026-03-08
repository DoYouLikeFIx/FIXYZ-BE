package com.fix.channel.dto.request;

import com.fix.channel.vo.MemberPasswordUpdateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class MemberPasswordUpdateRequest {

  @NotBlank
  private String currentPassword;

  @NotBlank
  @Pattern(
      regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
      message = "password must include uppercase, number, special char and be at least 8 chars"
  )
  private String newPassword;

  public MemberPasswordUpdateCommand toVo() {
    return MemberPasswordUpdateCommand.of(currentPassword, newPassword);
  }

  public String getCurrentPassword() {
    return currentPassword;
  }

  public void setCurrentPassword(String currentPassword) {
    this.currentPassword = currentPassword;
  }

  public String getNewPassword() {
    return newPassword;
  }

  public void setNewPassword(String newPassword) {
    this.newPassword = newPassword;
  }
}
