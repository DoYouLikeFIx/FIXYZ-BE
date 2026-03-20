package com.fix.channel.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.channel.vo.PasswordForgotChallengeResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PasswordForgotChallengeResponse(
    Integer challengeContractVersion,
    String challengeId,
    String challengeToken,
    String challengeType,
    Long challengeIssuedAtEpochMs,
    Long challengeExpiresAtEpochMs,
    int challengeTtlSeconds,
    PasswordForgotChallengeResult.ChallengePayload challengePayload
) {

  public static PasswordForgotChallengeResponse from(PasswordForgotChallengeResult result) {
    return new PasswordForgotChallengeResponse(
        result.getChallengeContractVersion(),
        result.getChallengeId(),
        result.getChallengeToken(),
        result.getChallengeType(),
        result.getChallengeIssuedAtEpochMs(),
        result.getChallengeExpiresAtEpochMs(),
        result.getChallengeTtlSeconds(),
        result.getChallengePayload()
    );
  }
}
