package com.fix.channel.service;

import com.fix.common.error.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PasswordRecoveryChallengeTelemetryService {

  private static final String METRIC_NAME = "auth.password_recovery.challenge.events";
  private static final String CLIENT_FAIL_CLOSED_METRIC_NAME =
      "auth.password_recovery.challenge.client_fail_closed";
  private static final String CLIENT_FAIL_CLOSED_DROPPED_METRIC_NAME =
      "auth.password_recovery.challenge.client_fail_closed_dropped";

  private final MeterRegistry meterRegistry;
  private final String environmentTag;

  public PasswordRecoveryChallengeTelemetryService(MeterRegistry meterRegistry, Environment environment) {
    this.meterRegistry = meterRegistry;
    this.environmentTag = resolveEnvironmentTag(environment);
  }

  public String recordBootstrapSuccess(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      String correlationId
  ) {
    return recordBootstrapSuccess(
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        correlationId,
        null
    );
  }

  public String recordBootstrapSuccess(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      String correlationId,
      String challengeIdHash
  ) {
    return record(
        "issue",
        "success",
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        null,
        correlationId,
        challengeIdHash
    );
  }

  public String recordBootstrapFailure(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      ErrorCode errorCode,
      String correlationId
  ) {
    return recordBootstrapFailure(
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        errorCode,
        correlationId,
        null
    );
  }

  public String recordBootstrapFailure(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      ErrorCode errorCode,
      String correlationId,
      String challengeIdHash
  ) {
    return record(
        "issue",
        bootstrapOutcome(errorCode),
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        errorCode,
        correlationId,
        challengeIdHash
    );
  }

  public String recordVerifySuccess(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      String correlationId
  ) {
    return recordVerifySuccess(
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        correlationId,
        null
    );
  }

  public String recordVerifySuccess(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      String correlationId,
      String challengeIdHash
  ) {
    return record(
        "verify",
        "success",
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        null,
        correlationId,
        challengeIdHash
    );
  }

  public String recordVerifyFailure(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      ErrorCode errorCode,
      String correlationId
  ) {
    return recordVerifyFailure(
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        errorCode,
        correlationId,
        null
    );
  }

  public String recordVerifyFailure(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      ErrorCode errorCode,
      String correlationId,
      String challengeIdHash
  ) {
    return record(
        "verify",
        verifyOutcome(errorCode),
        contractVersion,
        rolloutEnabled,
        challengeCapableCohort,
        errorCode,
        correlationId,
        challengeIdHash
    );
  }

  public String recordClientFailClosed(
      String reason,
      String surface,
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      String correlationId,
      String challengeIdHash
  ) {
    String safeContractVersion = sanitizeContractVersion(contractVersion);
    String safeReason = sanitizeReason(reason);
    String safeSurface = sanitizeSurface(surface);

    Counter.builder(CLIENT_FAIL_CLOSED_METRIC_NAME)
        .tag("reason", safeReason)
        .tag("surface", safeSurface)
        .tag("environment", environmentTag)
        .tag("contract_version", safeContractVersion)
        .tag("rollout_enabled", Boolean.toString(rolloutEnabled))
        .tag("challenge_capable_cohort", Boolean.toString(challengeCapableCohort))
        .register(meterRegistry)
        .increment();

    log.warn(
        "password_recovery_challenge_client_fail_closed reason={} surface={} contractVersion={} environment={} rolloutEnabled={} challengeCapableCohort={} traceId={} retryable=true challengeIdHash={}",
        safeReason,
        safeSurface,
        safeContractVersion,
        environmentTag,
        rolloutEnabled,
        challengeCapableCohort,
        correlationId,
        sanitizeChallengeIdHash(challengeIdHash)
    );
    return safeReason;
  }

  public String recordClientFailClosedDrop(
      String reason,
      String surface,
      String dropReason,
      String correlationId
  ) {
    String safeReason = sanitizeReason(reason);
    String safeSurface = sanitizeSurface(surface);
    String safeDropReason = sanitizeDropReason(dropReason);

    Counter.builder(CLIENT_FAIL_CLOSED_DROPPED_METRIC_NAME)
        .tag("reason", safeReason)
        .tag("surface", safeSurface)
        .tag("environment", environmentTag)
        .tag("drop_reason", safeDropReason)
        .register(meterRegistry)
        .increment();

    log.warn(
        "password_recovery_challenge_client_fail_closed_dropped reason={} surface={} dropReason={} environment={} traceId={}",
        safeReason,
        safeSurface,
        safeDropReason,
        environmentTag,
        correlationId
    );
    return safeDropReason;
  }

  private String record(
      String operation,
      String outcome,
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      ErrorCode errorCode,
      String correlationId,
      String challengeIdHash
  ) {
    String safeContractVersion = sanitizeContractVersion(contractVersion);
    String safeErrorCode = errorCode == null ? "none" : errorCode.code();
    boolean retryable = isRetryable(errorCode);

    Counter.builder(METRIC_NAME)
        .tag("operation", operation)
        .tag("outcome", outcome)
        .tag("environment", environmentTag)
        .tag("contract_version", safeContractVersion)
        .tag("rollout_enabled", Boolean.toString(rolloutEnabled))
        .tag("challenge_capable_cohort", Boolean.toString(challengeCapableCohort))
        .tag("error_code", safeErrorCode)
        .register(meterRegistry)
        .increment();

    if ("success".equals(outcome)) {
      log.info(
          "password_recovery_challenge_event operation={} outcome={} contractVersion={} environment={} rolloutEnabled={} challengeCapableCohort={} errorCode={} traceId={} retryable={} challengeIdHash={}",
          operation,
          outcome,
          safeContractVersion,
          environmentTag,
          rolloutEnabled,
          challengeCapableCohort,
          safeErrorCode,
          correlationId,
          retryable,
          sanitizeChallengeIdHash(challengeIdHash)
      );
    } else {
      log.warn(
          "password_recovery_challenge_event operation={} outcome={} contractVersion={} environment={} rolloutEnabled={} challengeCapableCohort={} errorCode={} traceId={} retryable={} challengeIdHash={}",
          operation,
          outcome,
          safeContractVersion,
          environmentTag,
          rolloutEnabled,
          challengeCapableCohort,
          safeErrorCode,
          correlationId,
          retryable,
          sanitizeChallengeIdHash(challengeIdHash)
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

  private String sanitizeReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return "unknown";
    }
    return reason;
  }

  private String sanitizeSurface(String surface) {
    if (surface == null || surface.isBlank()) {
      return "unknown";
    }
    return surface;
  }

  private String sanitizeDropReason(String dropReason) {
    if (dropReason == null || dropReason.isBlank()) {
      return "unknown";
    }
    return dropReason;
  }

  private boolean isRetryable(ErrorCode errorCode) {
    return errorCode == ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_BOOTSTRAP_UNAVAILABLE
        || errorCode == ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_VERIFY_UNAVAILABLE;
  }

  private String sanitizeChallengeIdHash(String challengeIdHash) {
    if (challengeIdHash == null || challengeIdHash.isBlank()) {
      return "none";
    }
    return challengeIdHash;
  }

  private String resolveEnvironmentTag(Environment environment) {
    String[] activeProfiles = Arrays.stream(environment.getActiveProfiles())
        .filter(profile -> profile != null && !profile.isBlank())
        .map(String::trim)
        .distinct()
        .sorted()
        .toArray(String[]::new);
    if (activeProfiles.length == 0) {
      return "default";
    }
    return String.join("+", activeProfiles);
  }
}
