package com.fix.channel.dto.response;

import com.fix.channel.vo.PasswordForgotChallengeResult;

public class PasswordForgotChallengeResponse {

  private final String challengeToken;
  private final String challengeType;
  private final int challengeTtlSeconds;

  private PasswordForgotChallengeResponse(String challengeToken, String challengeType, int challengeTtlSeconds) {
    this.challengeToken = challengeToken;
    this.challengeType = challengeType;
    this.challengeTtlSeconds = challengeTtlSeconds;
  }

  public static PasswordForgotChallengeResponse from(PasswordForgotChallengeResult result) {
    return new PasswordForgotChallengeResponse(
        result.getChallengeToken(),
        result.getChallengeType(),
        result.getChallengeTtlSeconds()
    );
  }

  public String getChallengeToken() {
    return challengeToken;
  }

  public String getChallengeType() {
    return challengeType;
  }

  public int getChallengeTtlSeconds() {
    return challengeTtlSeconds;
  }
}
