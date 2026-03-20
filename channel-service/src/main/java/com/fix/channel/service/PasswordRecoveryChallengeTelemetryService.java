package com.fix.channel.service;

import com.fix.common.error.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PasswordRecoveryChallengeTelemetryService {

  private static final String METRIC_NAME = "auth.password_recovery.challenge.events";

  private final MeterRegistry meterRegistry;

  public PasswordRecoveryChallengeTelemetryService(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public String recordBootstrapSuccess(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      String correlationId
  ) {
    return record(
        "issue",
        "success",
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        null,
        correlationId
    );
  }

  public String recordBootstrapFailure(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      ErrorCode errorCode,
      String correlationId
  ) {
    return record(
        "issue",
        bootstrapOutcome(errorCode),
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        errorCode,
        correlationId
    );
  }

  public String recordVerifySuccess(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      String correlationId
  ) {
    return record(
        "verify",
        "success",
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        null,
        correlationId
    );
  }

  public String recordVerifyFailure(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      ErrorCode errorCode,
      String correlationId
  ) {
    return record(
        "verify",
        verifyOutcome(errorCode),
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        errorCode,
        correlationId
    );
  }

  private String record(
      String operation,
      String outcome,
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      ErrorCode errorCode,
      String correlationId
  ) {
    String safeContractVersion = sanitizeContractVersion(contractVersion);
    String safeErrorCode = errorCode == null ? "none" : errorCode.code();

    Counter.builder(METRIC_NAME)
        .tag("operation", operation)
        .tag("outcome", outcome)
        .tag("contract_version", safeContractVersion)
        .tag("rollout_enabled", Boolean.toString(rolloutEnabled))
        .tag("challenge_capable_cohort", Boolean.toString(challengeCapableCohort))
        .tag("error_code", safeErrorCode)
        .register(meterRegistry)
        .increment();

    if ("success".equals(outcome)) {
      log.info(
          "password_recovery_challenge_event operation={} outcome={} contractVersion={} rolloutEnabled={} challengeCapableCohort={} errorCode={} correlationId={}",
          operation,
          outcome,
          safeContractVersion,
          rolloutEnabled,
          challengeCapableCohort,
          safeErrorCode,
          correlationId
      );
    } else {
      log.warn(
          "password_recovery_challenge_event operation={} outcome={} contractVersion={} rolloutEnabled={} challengeCapableCohort={} errorCode={} correlationId={}",
          operation,
          outcome,
          safeContractVersion,
          rolloutEnabled,
          challengeCapableCohort,
          safeErrorCode,
          correlationId
      );
    }
    return outcome;
  }

  private String bootstrapOutcome(ErrorCode errorCode) {
    if (errorCode == ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_BOOTSTRAP_UNAVAILABLE) {
      return "bootstrap_unavailable";
    }
    return "failure";
  }

  private String verifyOutcome(ErrorCode errorCode) {
    if (errorCode == ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID) {
      return "invalid_proof";
    }
    if (errorCode == ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_REPLAYED) {
      return "replay_rejected";
    }
    if (errorCode == ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_VERIFY_UNAVAILABLE) {
      return "verify_unavailable";
    }
    return "failure";
  }

  private String sanitizeContractVersion(String contractVersion) {
    if (contractVersion == null || contractVersion.isBlank()) {
      return "unknown";
    }
    return contractVersion;
  }
}
