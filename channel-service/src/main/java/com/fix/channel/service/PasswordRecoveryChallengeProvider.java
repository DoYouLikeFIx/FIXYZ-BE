package com.fix.channel.service;

import com.fix.channel.vo.PasswordForgotChallengeResult;
import jakarta.servlet.http.HttpServletRequest;

public interface PasswordRecoveryChallengeProvider {

  String ISSUE_CONTEXT_ROLLOUT_ENABLED_ATTRIBUTE =
      PasswordRecoveryChallengeProvider.class.getName() + ".issueContext.rolloutEnabled";
  String ISSUE_CONTEXT_CHALLENGE_CAPABLE_COHORT_ATTRIBUTE =
      PasswordRecoveryChallengeProvider.class.getName() + ".issueContext.challengeCapableCohort";

  record ChallengeEventContext(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort
  ) {
  }

  boolean isProofOfWorkProvider();

  boolean supportsToken(String challengeToken);

  PasswordForgotChallengeResult issue(String rawEmail, String normalizedEmail, HttpServletRequest request);

  void validate(
      String rawEmail,
      String normalizedEmail,
      String challengeToken,
      String challengeAnswer,
      HttpServletRequest request
  );

  default ChallengeEventContext describeVerifyContext(
      String challengeToken,
      ChallengeEventContext fallbackContext
  ) {
    return fallbackContext;
  }

  default String extractChallengeId(String challengeToken) {
    return null;
  }

  default String challengeContractVersionLabel() {
    return isProofOfWorkProvider() ? "2" : "legacy-v1";
  }
}
