package com.fix.channel.dto.request;

import com.fix.channel.vo.PasswordForgotCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class PasswordForgotRequest {

  @Email
  @NotBlank
  private String email;

  private String challengeToken;

  private String challengeAnswer;

  public PasswordForgotCommand toVo() {
    return PasswordForgotCommand.of(email, challengeToken, challengeAnswer);
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getChallengeToken() {
    return challengeToken;
  }

  public void setChallengeToken(String challengeToken) {
    this.challengeToken = challengeToken;
  }

  public String getChallengeAnswer() {
    return challengeAnswer;
  }

  public void setChallengeAnswer(String challengeAnswer) {
    this.challengeAnswer = challengeAnswer;
  }
}
