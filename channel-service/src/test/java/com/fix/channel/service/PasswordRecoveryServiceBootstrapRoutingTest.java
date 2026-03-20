package com.fix.channel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.PasswordResetTokenRepository;
import com.fix.channel.vo.PasswordForgotChallengeCommand;
import com.fix.channel.vo.PasswordForgotChallengeResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceBootstrapRoutingTest {

  @Mock
  private PasswordRecoveryRateLimitService rateLimitService;

  @Mock
  private PasswordRecoveryMailDispatcher mailDispatcher;

  @Mock
  private MemberRepository memberRepository;

  @Mock
  private PasswordResetTokenRepository passwordResetTokenRepository;

  @Mock
  private AuditLogService auditLogService;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private ChannelSessionInvalidationService channelSessionInvalidationService;

  @Mock
  private MfaRecoveryService mfaRecoveryService;

  @Mock
  private TaskExecutor taskExecutor;

  @Mock
  private PasswordRecoveryChallengeProvider legacyProvider;

  @Mock
  private PasswordRecoveryChallengeProvider proofOfWorkProvider;

  private PasswordRecoveryProperties properties;
  private PasswordRecoveryTokenService tokenService;
  private SimpleMeterRegistry meterRegistry;
  private PasswordRecoveryService service;

  @BeforeEach
  void setUp() {
    properties = new PasswordRecoveryProperties();
    tokenService = new PasswordRecoveryTokenService(properties);
    meterRegistry = new SimpleMeterRegistry();
    lenient().when(legacyProvider.isProofOfWorkProvider()).thenReturn(false);
    lenient().when(proofOfWorkProvider.isProofOfWorkProvider()).thenReturn(true);
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("itest");
    service = new PasswordRecoveryService(
        properties,
        rateLimitService,
        tokenService,
        List.of(legacyProvider, proofOfWorkProvider),
        new PasswordRecoveryChallengeTelemetryService(meterRegistry, environment),
        new PasswordRecoveryTimingEqualizer(properties, System::nanoTime, max -> 0L, millis -> {}),
        mailDispatcher,
        taskExecutor,
        memberRepository,
        passwordResetTokenRepository,
        auditLogService,
        passwordEncoder,
        channelSessionInvalidationService,
        mfaRecoveryService
    );
  }

  @Test
  void bootstrap_uses_legacy_provider_when_rollout_is_disabled() {
    properties.getChallenge().setV2Enabled(false);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);

    PasswordForgotChallengeResult legacyResult = PasswordForgotChallengeResult.legacy("legacy-token", "proof-of-work", 300);
    when(legacyProvider.issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    )).thenReturn(legacyResult);

    PasswordForgotChallengeResult result = service.bootstrapChallenge(PasswordForgotChallengeCommand.of("demo@fix.com"), request);

    assertEquals("legacy-token", result.getChallengeToken());
    verify(legacyProvider).issue("demo@fix.com", "demo@fix.com", request);
    verify(proofOfWorkProvider, never()).issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    );
  }

  @Test
  void bootstrap_uses_legacy_provider_when_request_is_out_of_cohort() {
    properties.getChallenge().setV2Enabled(true);
    properties.getChallenge().setCohortPercentage(0);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);

    PasswordForgotChallengeResult legacyResult = PasswordForgotChallengeResult.legacy("legacy-token-2", "proof-of-work", 300);
    when(legacyProvider.issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    )).thenReturn(legacyResult);

    PasswordForgotChallengeResult result = service.bootstrapChallenge(PasswordForgotChallengeCommand.of("demo@fix.com"), request);

    assertEquals("legacy-token-2", result.getChallengeToken());
    verify(legacyProvider).issue("demo@fix.com", "demo@fix.com", request);
    verify(proofOfWorkProvider, never()).issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    );
  }

  @Test
  void bootstrap_uses_proof_of_work_provider_when_cohort_matches() {
    properties.getChallenge().setV2Enabled(true);
    properties.getChallenge().setCohortPercentage(100);
    properties.getChallenge().setDifficultyBits(4);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);

    PasswordForgotChallengeResult proofResult = PasswordForgotChallengeResult.proofOfWork(
        "challenge-1",
        "v2.token",
        "proof-of-work",
        300,
        1L,
        2L,
        new PasswordForgotChallengeResult.ChallengePayload(
            "proof-of-work",
            new PasswordForgotChallengeResult.ProofOfWorkPayload(
                "SHA-256",
                "seed",
                4,
                "nonce-decimal",
                "{seed}:{nonce}",
                "utf-8",
                new PasswordForgotChallengeResult.SuccessCondition("leading-zero-bits", 4)
            )
        )
    );
    when(proofOfWorkProvider.issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    )).thenReturn(proofResult);

    PasswordForgotChallengeResult result = service.bootstrapChallenge(PasswordForgotChallengeCommand.of("demo@fix.com"), request);

    assertEquals("v2.token", result.getChallengeToken());
    assertEquals(2, result.getChallengeContractVersion());
    verify(proofOfWorkProvider).issue("demo@fix.com", "demo@fix.com", request);
    verify(legacyProvider, never()).issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    );
  }

  @Test
  void bootstrap_uses_deterministic_override_header_to_force_v2_or_legacy_paths() {
    properties.getChallenge().setV2Enabled(false);
    properties.getChallenge().setCohortPercentage(0);
    properties.getChallenge().setDeterministicOverrideEnabled(true);
    MockHttpServletRequest v2Request = new MockHttpServletRequest();
    v2Request.addHeader(properties.getChallenge().getDeterministicOverrideHeader(), "v2");
    v2Request.getSession(true);
    MockHttpServletRequest legacyRequest = new MockHttpServletRequest();
    legacyRequest.addHeader(properties.getChallenge().getDeterministicOverrideHeader(), "legacy-v1");
    legacyRequest.getSession(true);

    PasswordForgotChallengeResult proofResult = PasswordForgotChallengeResult.proofOfWork(
        "challenge-override-1",
        "v2.token.override",
        "proof-of-work",
        300,
        1L,
        2L,
        new PasswordForgotChallengeResult.ChallengePayload(
            "proof-of-work",
            new PasswordForgotChallengeResult.ProofOfWorkPayload(
                "SHA-256",
                "seed",
                4,
                "nonce-decimal",
                "{seed}:{nonce}",
                "utf-8",
                new PasswordForgotChallengeResult.SuccessCondition("leading-zero-bits", 4)
            )
        )
    );
    PasswordForgotChallengeResult legacyResult = PasswordForgotChallengeResult.legacy(
        "legacy-token-override",
        "proof-of-work",
        300
    );
    when(proofOfWorkProvider.issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    )).thenReturn(proofResult);
    when(legacyProvider.issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    )).thenReturn(legacyResult);

    PasswordForgotChallengeResult forcedV2 = service.bootstrapChallenge(
        PasswordForgotChallengeCommand.of("demo@fix.com"),
        v2Request
    );
    PasswordForgotChallengeResult forcedLegacy = service.bootstrapChallenge(
        PasswordForgotChallengeCommand.of("demo@fix.com"),
        legacyRequest
    );

    assertEquals(2, forcedV2.getChallengeContractVersion());
    assertEquals("legacy-token-override", forcedLegacy.getChallengeToken());
    verify(proofOfWorkProvider).issue("demo@fix.com", "demo@fix.com", v2Request);
    verify(legacyProvider).issue("demo@fix.com", "demo@fix.com", legacyRequest);
  }

  @Test
  void bootstrap_records_observability_hash_and_session_context_for_v2_challenges() {
    properties.getChallenge().setV2Enabled(true);
    properties.getChallenge().setCohortPercentage(100);
    properties.getChallenge().setObservabilitySecret("unit-observability-secret");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);

    PasswordForgotChallengeResult proofResult = PasswordForgotChallengeResult.proofOfWork(
        "challenge-obs-1",
        "v2.token.obs",
        "proof-of-work",
        300,
        10L,
        20L,
        new PasswordForgotChallengeResult.ChallengePayload(
            "proof-of-work",
            new PasswordForgotChallengeResult.ProofOfWorkPayload(
                "SHA-256",
                "seed",
                4,
                "nonce-decimal",
                "{seed}:{nonce}",
                "utf-8",
                new PasswordForgotChallengeResult.SuccessCondition("leading-zero-bits", 4)
            )
        )
    );
    when(proofOfWorkProvider.issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    )).thenReturn(proofResult);

    service.bootstrapChallenge(PasswordForgotChallengeCommand.of("demo@fix.com"), request);

    org.assertj.core.api.Assertions.assertThat(
        request.getSession(false).getAttribute(
            PasswordRecoveryService.class.getName() + ".challenge.pendingTelemetryContexts"
        )
    ).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .hasSize(1);

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, atLeastOnce()).record(auditCaptor.capture());
    AuditLog bootstrapAudit = auditCaptor.getAllValues().stream()
        .filter(log -> "PASSWORD_RECOVERY_CHALLENGE_ISSUED".equals(log.getAction()))
        .findFirst()
        .orElseThrow();
    assertNotNull(bootstrapAudit.getDetail());
    org.assertj.core.api.Assertions.assertThat(bootstrapAudit.getDetail())
        .contains("challengeIdHash=")
        .doesNotContain("challenge-obs-1");
  }

  @Test
  void client_fail_closed_uses_stored_context_for_metric_tags_and_audit_detail() {
    properties.getChallenge().setV2Enabled(true);
    properties.getChallenge().setCohortPercentage(100);
    properties.getChallenge().setObservabilitySecret("unit-observability-secret");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);

    PasswordForgotChallengeResult proofResult = PasswordForgotChallengeResult.proofOfWork(
        "challenge-obs-2",
        "v2.token.obs.2",
        "proof-of-work",
        300,
        10L,
        20L,
        new PasswordForgotChallengeResult.ChallengePayload(
            "proof-of-work",
            new PasswordForgotChallengeResult.ProofOfWorkPayload(
                "SHA-256",
                "seed",
                4,
                "nonce-decimal",
                "{seed}:{nonce}",
                "utf-8",
                new PasswordForgotChallengeResult.SuccessCondition("leading-zero-bits", 4)
            )
        )
    );
    when(proofOfWorkProvider.issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    )).thenReturn(proofResult);

    service.bootstrapChallenge(PasswordForgotChallengeCommand.of("demo@fix.com"), request);
    service.recordClientFailClosed("clock-skew", "forgot-password-web", 10L, request);

    Counter counter = meterRegistry.find("auth.password_recovery.challenge.client_fail_closed")
        .tags(
            "reason", "clock-skew",
            "surface", "forgot-password-web",
            "contract_version", "2",
            "rollout_enabled", "true",
            "challenge_capable_cohort", "true"
        )
        .counter();
    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
    assertEquals("itest", counter.getId().getTag("environment"));

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, atLeastOnce()).record(auditCaptor.capture());
    AuditLog failClosedAudit = auditCaptor.getAllValues().stream()
        .filter(log -> log.getDetail() != null && log.getDetail().contains("outcome=client_fail_closed"))
        .findFirst()
        .orElseThrow();
    org.assertj.core.api.Assertions.assertThat(failClosedAudit.getDetail())
        .contains("clientFailClosedReason=clock-skew")
        .contains("challengeIdHash=")
        .doesNotContain("challenge-obs-2");
  }

  @Test
  void client_fail_closed_requires_pending_bootstrap_context() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    service.recordClientFailClosed("clock-skew", "forgot-password-web", 10L, request);

    Counter counter = meterRegistry.find("auth.password_recovery.challenge.client_fail_closed")
        .tags(
            "reason", "clock-skew",
            "surface", "forgot-password-web"
        )
        .counter();
    org.assertj.core.api.Assertions.assertThat(counter).isNull();
    Counter droppedCounter = meterRegistry.find("auth.password_recovery.challenge.client_fail_closed_dropped")
        .tags(
            "reason", "clock-skew",
            "surface", "forgot-password-web",
            "drop_reason", "missing-session"
        )
        .counter();
    assertNotNull(droppedCounter);
    assertEquals(1.0d, droppedCounter.count());
    verify(auditLogService, never()).record(org.mockito.ArgumentMatchers.any(AuditLog.class));
  }

  @Test
  void client_fail_closed_drops_mismatched_issue_timestamp_without_recording_metrics() {
    properties.getChallenge().setV2Enabled(true);
    properties.getChallenge().setCohortPercentage(100);
    properties.getChallenge().setObservabilitySecret("unit-observability-secret");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);

    PasswordForgotChallengeResult proofResult = PasswordForgotChallengeResult.proofOfWork(
        "challenge-obs-mismatch",
        "v2.token.obs.mismatch",
        "proof-of-work",
        300,
        10L,
        20L,
        new PasswordForgotChallengeResult.ChallengePayload(
            "proof-of-work",
            new PasswordForgotChallengeResult.ProofOfWorkPayload(
                "SHA-256",
                "seed",
                4,
                "nonce-decimal",
                "{seed}:{nonce}",
                "utf-8",
                new PasswordForgotChallengeResult.SuccessCondition("leading-zero-bits", 4)
            )
        )
    );
    when(proofOfWorkProvider.issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    )).thenReturn(proofResult);

    service.bootstrapChallenge(PasswordForgotChallengeCommand.of("demo@fix.com"), request);
    service.recordClientFailClosed("clock-skew", "forgot-password-web", 999L, request);

    Counter counter = meterRegistry.find("auth.password_recovery.challenge.client_fail_closed")
        .tags(
            "reason", "clock-skew",
            "surface", "forgot-password-web"
        )
        .counter();
    org.assertj.core.api.Assertions.assertThat(counter).isNull();
    Counter droppedCounter = meterRegistry.find("auth.password_recovery.challenge.client_fail_closed_dropped")
        .tags(
            "reason", "clock-skew",
            "surface", "forgot-password-web",
            "drop_reason", "timestamp-mismatch"
        )
        .counter();
    assertNotNull(droppedCounter);
    assertEquals(1.0d, droppedCounter.count());
    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, atLeastOnce()).record(auditCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(auditCaptor.getAllValues())
        .noneSatisfy(log -> org.assertj.core.api.Assertions.assertThat(log.getDetail())
            .contains("outcome=client_fail_closed"));
  }

  @Test
  void legacy_bootstrap_does_not_clear_existing_pending_v2_contexts() {
    properties.getChallenge().setV2Enabled(true);
    properties.getChallenge().setCohortPercentage(100);
    properties.getChallenge().setObservabilitySecret("unit-observability-secret");
    MockHttpServletRequest v2Request = new MockHttpServletRequest();
    v2Request.getSession(true);
    MockHttpServletRequest legacyRequest = new MockHttpServletRequest();
    legacyRequest.setSession(v2Request.getSession());

    PasswordForgotChallengeResult proofResult = PasswordForgotChallengeResult.proofOfWork(
        "challenge-keep-context",
        "v2.token.keep-context",
        "proof-of-work",
        300,
        10L,
        20L,
        new PasswordForgotChallengeResult.ChallengePayload(
            "proof-of-work",
            new PasswordForgotChallengeResult.ProofOfWorkPayload(
                "SHA-256",
                "seed",
                4,
                "nonce-decimal",
                "{seed}:{nonce}",
                "utf-8",
                new PasswordForgotChallengeResult.SuccessCondition("leading-zero-bits", 4)
            )
        )
    );
    PasswordForgotChallengeResult legacyResult = PasswordForgotChallengeResult.legacy("legacy-token", "proof-of-work", 300);
    when(proofOfWorkProvider.issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    )).thenReturn(proofResult);
    when(legacyProvider.issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    )).thenReturn(legacyResult);

    service.bootstrapChallenge(PasswordForgotChallengeCommand.of("demo@fix.com"), v2Request);
    properties.getChallenge().setV2Enabled(false);
    service.bootstrapChallenge(PasswordForgotChallengeCommand.of("demo@fix.com"), legacyRequest);
    service.recordClientFailClosed("clock-skew", "forgot-password-web", 10L, legacyRequest);

    Counter counter = meterRegistry.find("auth.password_recovery.challenge.client_fail_closed")
        .tags(
            "reason", "clock-skew",
            "surface", "forgot-password-web",
            "contract_version", "2",
            "rollout_enabled", "true",
            "challenge_capable_cohort", "true"
        )
        .counter();
    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
  }

  @Test
  void client_fail_closed_omits_hash_when_issue_timestamp_is_ambiguous() {
    properties.getChallenge().setV2Enabled(true);
    properties.getChallenge().setCohortPercentage(100);
    properties.getChallenge().setObservabilitySecret("unit-observability-secret");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);

    PasswordForgotChallengeResult firstResult = PasswordForgotChallengeResult.proofOfWork(
        "challenge-ambiguous-a",
        "v2.token.ambiguous.a",
        "proof-of-work",
        300,
        10L,
        20L,
        new PasswordForgotChallengeResult.ChallengePayload(
            "proof-of-work",
            new PasswordForgotChallengeResult.ProofOfWorkPayload(
                "SHA-256",
                "seed-a",
                4,
                "nonce-decimal",
                "{seed}:{nonce}",
                "utf-8",
                new PasswordForgotChallengeResult.SuccessCondition("leading-zero-bits", 4)
            )
        )
    );
    PasswordForgotChallengeResult secondResult = PasswordForgotChallengeResult.proofOfWork(
        "challenge-ambiguous-b",
        "v2.token.ambiguous.b",
        "proof-of-work",
        300,
        10L,
        20L,
        new PasswordForgotChallengeResult.ChallengePayload(
            "proof-of-work",
            new PasswordForgotChallengeResult.ProofOfWorkPayload(
                "SHA-256",
                "seed-b",
                4,
                "nonce-decimal",
                "{seed}:{nonce}",
                "utf-8",
                new PasswordForgotChallengeResult.SuccessCondition("leading-zero-bits", 4)
            )
        )
    );
    when(proofOfWorkProvider.issue(
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers.<jakarta.servlet.http.HttpServletRequest>any()
    )).thenReturn(firstResult, secondResult);

    service.bootstrapChallenge(PasswordForgotChallengeCommand.of("demo@fix.com"), request);
    service.bootstrapChallenge(PasswordForgotChallengeCommand.of("demo@fix.com"), request);
    service.recordClientFailClosed("validity-untrusted", "forgot-password-web", 10L, request);

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, atLeastOnce()).record(auditCaptor.capture());
    AuditLog failClosedAudit = auditCaptor.getAllValues().stream()
        .filter(log -> log.getDetail() != null && log.getDetail().contains("outcome=client_fail_closed"))
        .findFirst()
        .orElseThrow();
    org.assertj.core.api.Assertions.assertThat(failClosedAudit.getDetail())
        .contains("clientFailClosedReason=validity-untrusted")
        .doesNotContain("challenge-ambiguous-a")
        .doesNotContain("challenge-ambiguous-b")
        .doesNotContain("challengeIdHash=");
  }
}
