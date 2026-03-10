package com.fix.channel.vo;

public class PasswordForgotChallengeResult {

  private final String challengeToken;
  private final String challengeType;
  private final int challengeTtlSeconds;

  private PasswordForgotChallengeResult(String challengeToken, String challengeType, int challengeTtlSeconds) {
    this.challengeToken = challengeToken;
    this.challengeType = challengeType;
    this.challengeTtlSeconds = challengeTtlSeconds;
  }

  public static PasswordForgotChallengeResult of(String challengeToken, String challengeType, int challengeTtlSeconds) {
    return new PasswordForgotChallengeResult(challengeToken, challengeType, challengeTtlSeconds);
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
