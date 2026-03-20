package com.fix.channel.vo;

public class PasswordForgotChallengeResult {

  private final Integer challengeContractVersion;
  private final String challengeId;
  private final String challengeToken;
  private final String challengeType;
  private final Long challengeIssuedAtEpochMs;
  private final Long challengeExpiresAtEpochMs;
  private final int challengeTtlSeconds;
  private final ChallengePayload challengePayload;

  private PasswordForgotChallengeResult(String challengeToken, String challengeType, int challengeTtlSeconds) {
    this.challengeContractVersion = null;
    this.challengeId = null;
    this.challengeToken = challengeToken;
    this.challengeType = challengeType;
    this.challengeIssuedAtEpochMs = null;
    this.challengeExpiresAtEpochMs = null;
    this.challengeTtlSeconds = challengeTtlSeconds;
    this.challengePayload = null;
  }

  private PasswordForgotChallengeResult(
      Integer challengeContractVersion,
      String challengeId,
      String challengeToken,
      String challengeType,
      Long challengeIssuedAtEpochMs,
      Long challengeExpiresAtEpochMs,
      int challengeTtlSeconds,
      ChallengePayload challengePayload
  ) {
    this.challengeContractVersion = challengeContractVersion;
    this.challengeId = challengeId;
    this.challengeToken = challengeToken;
    this.challengeType = challengeType;
    this.challengeIssuedAtEpochMs = challengeIssuedAtEpochMs;
    this.challengeExpiresAtEpochMs = challengeExpiresAtEpochMs;
    this.challengeTtlSeconds = challengeTtlSeconds;
    this.challengePayload = challengePayload;
  }

  public static PasswordForgotChallengeResult legacy(String challengeToken, String challengeType, int challengeTtlSeconds) {
    return new PasswordForgotChallengeResult(challengeToken, challengeType, challengeTtlSeconds);
  }

  public static PasswordForgotChallengeResult proofOfWork(
      String challengeId,
      String challengeToken,
      String challengeType,
      int challengeTtlSeconds,
      long challengeIssuedAtEpochMs,
      long challengeExpiresAtEpochMs,
      ChallengePayload challengePayload
  ) {
    return new PasswordForgotChallengeResult(
        2,
        challengeId,
        challengeToken,
        challengeType,
        challengeIssuedAtEpochMs,
        challengeExpiresAtEpochMs,
        challengeTtlSeconds,
        challengePayload
    );
  }

  public Integer getChallengeContractVersion() {
    return challengeContractVersion;
  }

  public String getChallengeId() {
    return challengeId;
  }

  public String getChallengeToken() {
    return challengeToken;
  }

  public String getChallengeType() {
    return challengeType;
  }

  public Long getChallengeIssuedAtEpochMs() {
    return challengeIssuedAtEpochMs;
  }

  public Long getChallengeExpiresAtEpochMs() {
    return challengeExpiresAtEpochMs;
  }

  public int getChallengeTtlSeconds() {
    return challengeTtlSeconds;
  }

  public ChallengePayload getChallengePayload() {
    return challengePayload;
  }

  public record ChallengePayload(String kind, ProofOfWorkPayload proofOfWork) {
  }

  public record ProofOfWorkPayload(
      String algorithm,
      String seed,
      int difficultyBits,
      String answerFormat,
      String inputTemplate,
      String inputEncoding,
      SuccessCondition successCondition
  ) {
  }

  public record SuccessCondition(String type, int minimum) {
  }
}
