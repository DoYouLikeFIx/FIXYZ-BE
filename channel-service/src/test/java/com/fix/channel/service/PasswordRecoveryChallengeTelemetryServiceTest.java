package com.fix.channel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fix.common.error.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PasswordRecoveryChallengeTelemetryServiceTest {

  @Test
  void recordVerifyFailure_tracksInvalidProofWithVerifyTags() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("itest");
    PasswordRecoveryChallengeTelemetryService service =
        new PasswordRecoveryChallengeTelemetryService(meterRegistry, environment);

    String outcome = service.recordVerifyFailure(
        "2",
        true,
        false,
        ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
        "corr-verify-invalid"
    );

    assertEquals("invalid_proof", outcome);
    Counter counter = meterRegistry.find("auth.password_recovery.challenge.events")
        .tags(
            "operation", "verify",
            "outcome", "invalid_proof",
            "environment", "itest",
            "contract_version", "2",
            "rollout_enabled", "true",
            "challenge_capable_cohort", "false",
            "error_code", "AUTH-022"
        )
        .counter();
    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
  }

  @Test
  void recordBootstrapFailure_tracksInfrastructureFailureAndSanitizesBlankContractVersion() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("itest");
    PasswordRecoveryChallengeTelemetryService service =
        new PasswordRecoveryChallengeTelemetryService(meterRegistry, environment);

    String outcome = service.recordBootstrapFailure(
        "",
        false,
        true,
        ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_BOOTSTRAP_UNAVAILABLE,
        "corr-bootstrap-unavailable"
    );

    assertEquals("bootstrap_unavailable", outcome);
    Counter counter = meterRegistry.find("auth.password_recovery.challenge.events")
        .tags(
            "operation", "issue",
            "outcome", "bootstrap_unavailable",
            "environment", "itest",
            "contract_version", "unknown",
            "rollout_enabled", "false",
            "challenge_capable_cohort", "true",
            "error_code", "AUTH-023"
        )
        .counter();
    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
  }

  @Test
  void recordVerifyFailure_tracksReplayRejectionTags() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("itest");
    PasswordRecoveryChallengeTelemetryService service =
        new PasswordRecoveryChallengeTelemetryService(meterRegistry, environment);

    String outcome = service.recordVerifyFailure(
        "legacy-v1",
        false,
        false,
        ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_REPLAYED,
        "corr-verify-replay"
    );

    assertEquals("replay_rejected", outcome);
    Counter counter = meterRegistry.find("auth.password_recovery.challenge.events")
        .tags(
            "operation", "verify",
            "outcome", "replay_rejected",
            "environment", "itest",
            "contract_version", "legacy-v1",
            "rollout_enabled", "false",
            "challenge_capable_cohort", "false",
            "error_code", "AUTH-024"
        )
        .counter();
    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
  }

  @Test
  void recordVerifyFailure_tracksVerifyUnavailableTags() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("itest");
    PasswordRecoveryChallengeTelemetryService service =
        new PasswordRecoveryChallengeTelemetryService(meterRegistry, environment);

    String outcome = service.recordVerifyFailure(
        "2",
        true,
        true,
        ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_VERIFY_UNAVAILABLE,
        "corr-verify-unavailable"
    );

    assertEquals("verify_unavailable", outcome);
    Counter counter = meterRegistry.find("auth.password_recovery.challenge.events")
        .tags(
            "operation", "verify",
            "outcome", "verify_unavailable",
            "environment", "itest",
            "contract_version", "2",
            "rollout_enabled", "true",
            "challenge_capable_cohort", "true",
            "error_code", "AUTH-025"
        )
        .counter();
    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
  }

  @Test
  void recordClientFailClosed_tracksExactReasonAndSurfaceTags() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("itest");
    PasswordRecoveryChallengeTelemetryService service =
        new PasswordRecoveryChallengeTelemetryService(meterRegistry, environment);

    String reason = service.recordClientFailClosed(
        "clock-skew",
        "forgot-password-web",
        "2",
        true,
        true,
        "corr-client-fail-closed",
        "8c7f1a2b3c4d5e6f7a8b9c0d"
    );

    assertEquals("clock-skew", reason);
    Counter counter = meterRegistry.find("auth.password_recovery.challenge.client_fail_closed")
        .tags(
            "reason", "clock-skew",
            "surface", "forgot-password-web",
            "environment", "itest",
            "contract_version", "2",
            "rollout_enabled", "true",
            "challenge_capable_cohort", "true"
        )
        .counter();
    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
  }

  @Test
  void recordClientFailClosedDrop_tracksDropReasonAndCombinesActiveProfiles() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod", "canary");
    PasswordRecoveryChallengeTelemetryService service =
        new PasswordRecoveryChallengeTelemetryService(meterRegistry, environment);

    String dropReason = service.recordClientFailClosedDrop(
        "clock-skew",
        "forgot-password-web",
        "timestamp-mismatch",
        "corr-client-fail-closed-dropped"
    );

    assertEquals("timestamp-mismatch", dropReason);
    Counter counter = meterRegistry.find("auth.password_recovery.challenge.client_fail_closed_dropped")
        .tags(
            "reason", "clock-skew",
            "surface", "forgot-password-web",
            "environment", "canary+prod",
            "drop_reason", "timestamp-mismatch"
        )
        .counter();
    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
  }
}
