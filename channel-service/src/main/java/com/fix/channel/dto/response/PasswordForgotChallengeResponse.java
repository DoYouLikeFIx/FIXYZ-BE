package com.fix.channel.dto.response;

import com.fix.channel.vo.PasswordForgotChallengeResult;

public record PasswordForgotChallengeResponse(
    String challengeToken,
    String challengeType,
    int challengeTtlSeconds
) {

  public static PasswordForgotChallengeResponse from(PasswordForgotChallengeResult result) {
    return new PasswordForgotChallengeResponse(
        result.getChallengeToken(),
        result.getChallengeType(),
        result.getChallengeTtlSeconds()
    );
  }
}
