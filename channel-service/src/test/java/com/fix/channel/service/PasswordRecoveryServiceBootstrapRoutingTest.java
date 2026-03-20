package com.fix.channel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.PasswordResetTokenRepository;
import com.fix.channel.vo.PasswordForgotChallengeCommand;
import com.fix.channel.vo.PasswordForgotChallengeResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
  private PasswordRecoveryService service;

  @BeforeEach
  void setUp() {
    properties = new PasswordRecoveryProperties();
    tokenService = new PasswordRecoveryTokenService(properties);
    lenient().when(legacyProvider.isProofOfWorkProvider()).thenReturn(false);
    lenient().when(proofOfWorkProvider.isProofOfWorkProvider()).thenReturn(true);
    service = new PasswordRecoveryService(
        properties,
        rateLimitService,
        tokenService,
        List.of(legacyProvider, proofOfWorkProvider),
        new PasswordRecoveryChallengeTelemetryService(new SimpleMeterRegistry()),
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
}
