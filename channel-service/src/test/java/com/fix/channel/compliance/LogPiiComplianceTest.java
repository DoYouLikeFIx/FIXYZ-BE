package com.fix.channel.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Member;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.PasswordResetToken;
import com.fix.channel.entity.SecurityEvent;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.ManualRecoveryQueueEntryRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.repository.PasswordResetTokenRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.service.AccountPositionService;
import com.fix.channel.service.AdminMemberSessionService;
import com.fix.channel.service.AuditLogService;
import com.fix.channel.service.ChannelSessionInvalidationService;
import com.fix.channel.service.LoggingPasswordRecoveryMailDispatcher;
import com.fix.channel.service.LoginTokenService;
import com.fix.channel.service.MfaRecoveryService;
import com.fix.channel.service.MfaRecoveryTokenService;
import com.fix.channel.service.OrderSessionOtpChallengeService;
import com.fix.channel.service.OrderSessionPersistenceService;
import com.fix.channel.service.OrderSessionRateLimitService;
import com.fix.channel.service.OrderSessionService;
import com.fix.channel.service.OrderSessionTtlStore;
import com.fix.channel.service.OtpVerifyRateLimitService;
import com.fix.channel.service.PasswordRecoveryChallengeProvider;
import com.fix.channel.service.PasswordRecoveryChallengeService;
import com.fix.channel.service.PasswordRecoveryChallengeTelemetryService;
import com.fix.channel.service.PasswordRecoveryMailDispatcher;
import com.fix.channel.service.PasswordRecoveryRateLimitService;
import com.fix.channel.service.PasswordRecoveryService;
import com.fix.channel.service.PasswordRecoveryTimingEqualizer;
import com.fix.channel.service.PasswordRecoveryTokenService;
import com.fix.channel.service.SecurityEventService;
import com.fix.channel.service.SessionOwnershipValidator;
import com.fix.channel.service.TotpEnrollRateLimitService;
import com.fix.channel.service.TotpReplayGuardService;
import com.fix.channel.service.TotpService;
import com.fix.channel.vo.AccountPositionResult;
import com.fix.channel.vo.AdminActorContext;
import com.fix.channel.vo.MfaRecoveryRebindCommand;
import com.fix.channel.vo.MfaRecoveryRebindConfirmCommand;
import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.channel.vo.PasswordForgotCommand;
import com.fix.channel.vo.PasswordResetCommand;
import com.fix.channel.vo.PasswordResetContinuationResult;
import com.fix.channel.vo.TotpRebindBootstrapResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class LogPiiComplianceTest {

  private static final String RAW_ACCOUNT_NUMBER = "110123456789";
  private static final String RAW_PASSWORD = "Abcd1234!";
  private static final String RAW_OTP = "654321";
  private static final String RAW_SESSION_TOKEN = "session-123";
  private static final String RAW_AUTHORIZATION = "Bearer abc.def.ghi";
  private static final String RAW_LEGACY_SESSION = "legacy-456";
  private static final String RAW_GENERIC_TOKEN = "raw-reset-token-000";
  private static final String RAW_EMAIL = "recover.user@fixyz.com";
  private static final String RAW_CLIENT_IP = "198.51.100.123";
  private static final String MASKED_CLIENT_IP = "198.51.100.0";
  private static final String RAW_JSON_PAYLOAD =
      "{\"accountNumber\":110123456789,\"password\":\"Abcd1234!\","
          + "\"authorization\":\"Bearer abc.def.ghi\",\"cookie\":\"session-123\",\"otpCode\":654321,"
          + "\"token\":\"" + RAW_GENERIC_TOKEN + "\",\"email\":\"" + RAW_EMAIL + "\",\"clientIp\":\"" + RAW_CLIENT_IP + "\"}";
  private static final String RAW_CONTEXT =
      "authorization=" + RAW_AUTHORIZATION
          + ", cookie=" + RAW_SESSION_TOKEN
          + ", token=" + RAW_GENERIC_TOKEN
          + ", email=" + RAW_EMAIL
          + ", clientIp=" + RAW_CLIENT_IP
          + ", payload=" + RAW_JSON_PAYLOAD;

  @Mock
  private AuditLogRepository auditLogRepository;

  @Mock
  private SecurityEventRepository securityEventRepository;

  @Mock
  private PlatformTransactionManager transactionManager;

  private final AtomicLong auditIdSequence = new AtomicLong(1L);
  private final AtomicLong securityEventIdSequence = new AtomicLong(1L);
  private List<AuditLog> persistedAuditLogs;
  private List<SecurityEvent> persistedSecurityEvents;
  private AuditLogService auditLogService;
  private SecurityEventService securityEventService;

  @BeforeEach
  void setUp() {
    persistedAuditLogs = new ArrayList<>();
    persistedSecurityEvents = new ArrayList<>();
    lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
    lenient().when(auditLogRepository.saveAndFlush(any(AuditLog.class)))
        .thenAnswer(invocation -> captureAuditLog(invocation.getArgument(0)));
    lenient().when(securityEventRepository.saveAndFlush(any(SecurityEvent.class)))
        .thenAnswer(invocation -> captureSecurityEvent(invocation.getArgument(0)));
    auditLogService = new AuditLogService(auditLogRepository, transactionManager);
    securityEventService = new SecurityEventService(securityEventRepository, transactionManager);
  }

  @Test
  void shouldKeepCapturedLogsFreeOfRawSensitiveValues(CapturedOutput output) {
    String rawCookieHeader = "SESSION=" + RAW_SESSION_TOKEN + "; JSESSIONID=" + RAW_LEGACY_SESSION;

    AuditLog auditLog = AuditLog.of(
        101L,
        AuditAction.AUTH_LOGIN_SUCCESS,
        "SESSION",
        RAW_SESSION_TOKEN,
        "accountNumber=" + RAW_ACCOUNT_NUMBER
            + ", password=" + RAW_PASSWORD
            + ", otp=" + RAW_OTP
            + ", sessionToken=" + RAW_SESSION_TOKEN
            + ", authorization=" + RAW_AUTHORIZATION
            + ", cookie=" + RAW_SESSION_TOKEN
            + ", payload=" + RAW_JSON_PAYLOAD
            + ", Cookie: " + rawCookieHeader,
        RAW_CLIENT_IP,
        RAW_CONTEXT,
        "123e4567-e89b-42d3-a456-426614174299"
    );
    SecurityEvent securityEvent = SecurityEvent.of(
        101L,
        "ACCOUNT_LOCKED",
        RAW_CLIENT_IP,
        RAW_CONTEXT,
        "HIGH"
    ).withDetail(
        "payload=" + RAW_JSON_PAYLOAD
            + ", loginToken=" + RAW_SESSION_TOKEN
            + ", otp=" + RAW_OTP
    ).withCorrelationId("123e4567-e89b-42d3-a456-426614174299");

    when(auditLogRepository.saveAndFlush(any(AuditLog.class)))
        .thenThrow(new DataIntegrityViolationException("simulated audit failure"));
    when(securityEventRepository.saveAndFlush(any(SecurityEvent.class)))
        .thenThrow(new DataIntegrityViolationException("simulated security event failure"));

    auditLogService.record(auditLog);
    securityEventService.record(securityEvent);

    assertThat(auditLog.getTargetId()).isEqualTo("[REDACTED]");
    assertThat(auditLog.getDetail())
        .contains("accountNumber=110-****-6789")
        .contains("password=[REDACTED]")
        .contains("otp=[REDACTED]")
        .contains("sessionToken=[REDACTED]")
        .contains("authorization=[REDACTED]")
        .contains("cookie=[REDACTED]")
        .contains("\"accountNumber\":\"110-****-6789\"")
        .contains("\"password\":\"[REDACTED]\"")
        .contains("\"authorization\":\"[REDACTED]\"")
        .contains("\"cookie\":\"[REDACTED]\"")
        .contains("\"otpCode\":\"[REDACTED]\"")
        .contains("\"token\":\"[REDACTED]\"")
        .contains("\"email\":\"[REDACTED]\"")
        .contains(MASKED_CLIENT_IP)
        .contains("Cookie: [REDACTED]");
    assertThat(auditLog.getIpAddress()).isEqualTo(MASKED_CLIENT_IP);
    assertSanitized(auditLog.getDetail());
    assertSanitized(auditLog.getUserAgent());

    assertThat(securityEvent.getDetail())
        .contains("\"accountNumber\":\"110-****-6789\"")
        .contains("\"password\":\"[REDACTED]\"")
        .contains("\"authorization\":\"[REDACTED]\"")
        .contains("\"cookie\":\"[REDACTED]\"")
        .contains("\"otpCode\":\"[REDACTED]\"")
        .contains("\"token\":\"[REDACTED]\"")
        .contains("\"email\":\"[REDACTED]\"")
        .contains(MASKED_CLIENT_IP)
        .contains("loginToken=[REDACTED]")
        .contains("otp=[REDACTED]");
    assertThat(securityEvent.getIpAddress()).isEqualTo(MASKED_CLIENT_IP);
    assertSanitized(securityEvent.getDetail());
    assertSanitized(securityEvent.getUserAgent());

    assertThat(output)
        .contains("Failed to persist audit log")
        .contains("Failed to persist security event");
    assertSanitized(output.toString());
  }

  @Test
  void shouldExerciseRepresentativeServicePathsAndKeepCapturedArtifactsSanitized(CapturedOutput output) {
    exerciseOrderSessionPath(auditLogService, securityEventService);
    String rawForgotResetToken = exercisePasswordRecoveryForgotPath(auditLogService);
    PasswordResetContinuationResult resetResult = exercisePasswordRecoveryResetPath(auditLogService);
    exerciseAdminSessionPath(auditLogService);
    exerciseMfaIssueRecoveryPath(auditLogService, securityEventService);
    TotpRebindBootstrapResult bootstrapResult =
        exerciseMfaBootstrapPath(auditLogService, securityEventService);
    exerciseMfaConfirmPath(
        auditLogService,
        securityEventService,
        bootstrapResult.getRebindToken(),
        bootstrapResult.getEnrollmentToken()
    );

    assertThat(persistedAuditLogs).hasSizeGreaterThanOrEqualTo(9);

    AuditLog orderAudit = findAudit(persistedAuditLogs, AuditAction.ORDER_SESSION_CREATE.value());
    assertThat(orderAudit.getTargetId()).isNotBlank();
    assertThat(orderAudit.getDetail()).contains("clOrdId=CL-PII-ORDER-001");
    assertSanitized(orderAudit.getDetail());

    AuditLog forgotMemberAudit = findAudit(persistedAuditLogs, AuditAction.PASSWORD_RECOVERY_FORGOT.value(), "MEMBER");
    assertThat(forgotMemberAudit.getUserAgent())
        .contains("authorization=[REDACTED]")
        .contains("cookie=[REDACTED]")
        .contains("token=[REDACTED]")
        .contains("email=[REDACTED]")
        .contains("clientIp=" + MASKED_CLIENT_IP)
        .contains("\"accountNumber\":\"110-****-6789\"")
        .contains("\"password\":\"[REDACTED]\"")
        .contains("\"otpCode\":\"[REDACTED]\"")
        .contains("\"token\":\"[REDACTED]\"")
        .contains("\"email\":\"[REDACTED]\"");
    assertThat(forgotMemberAudit.getIpAddress()).isEqualTo(MASKED_CLIENT_IP);
    assertThat(forgotMemberAudit.getTargetId()).isEqualTo("601");
    assertSanitized(forgotMemberAudit.getTargetId(), rawForgotResetToken, RAW_EMAIL);
    assertSanitized(forgotMemberAudit.getUserAgent(), rawForgotResetToken);

    AuditLog forgotTokenAudit =
        findAudit(persistedAuditLogs, AuditAction.PASSWORD_RECOVERY_FORGOT.value(), "PASSWORD_RECOVERY");
    assertThat(forgotTokenAudit.getTargetId()).isEqualTo("601");
    assertThat(forgotTokenAudit.getIpAddress()).isEqualTo(MASKED_CLIENT_IP);
    assertSanitized(forgotTokenAudit.getTargetId(), rawForgotResetToken);
    assertSanitized(forgotTokenAudit.getUserAgent(), rawForgotResetToken);

    AuditLog resetAudit = findAudit(persistedAuditLogs, AuditAction.PASSWORD_RECOVERY_RESET.value(), "MEMBER");
    assertThat(resetResult.hasMfaRecoveryProof()).isTrue();
    assertThat(resetAudit.getTargetId()).isEqualTo("602");
    assertThat(resetAudit.getIpAddress()).isEqualTo(MASKED_CLIENT_IP);
    assertSanitized(resetAudit.getTargetId(), resetResult.getMfaRecoveryProof());
    assertSanitized(resetAudit.getUserAgent(), resetResult.getMfaRecoveryProof());

    AuditLog adminAudit = findAudit(persistedAuditLogs, AuditAction.ADMIN_FORCE_LOGOUT.value());
    assertSanitized(adminAudit.getTargetId());
    assertSanitized(adminAudit.getUserAgent());

    AuditLog proofAudit = findAudit(persistedAuditLogs, AuditAction.AUTH_MFA_RECOVERY_PROOF_ISSUED.value(), "MFA_RECOVERY");
    assertThat(proofAudit.getTargetId()).isEqualTo("701");
    assertSanitized(proofAudit.getUserAgent());

    AuditLog bootstrapAudit = findAudit(persistedAuditLogs, AuditAction.AUTH_TOTP_REBIND_INITIATED.value(), "TOTP");
    assertThat(bootstrapAudit.getTargetId()).isEqualTo("M-PII-MFA-702");
    assertSanitized(bootstrapAudit.getUserAgent(), bootstrapResult.getRebindToken(), bootstrapResult.getEnrollmentToken());

    AuditLog confirmAudit = findAudit(persistedAuditLogs, AuditAction.AUTH_TOTP_REBIND_CONFIRMED.value(), "TOTP");
    assertThat(confirmAudit.getTargetId()).isEqualTo("M-PII-MFA-703");
    assertSanitized(confirmAudit.getUserAgent(), bootstrapResult.getRebindToken(), bootstrapResult.getEnrollmentToken());

    assertThat(persistedSecurityEvents).hasSizeGreaterThanOrEqualTo(3);
    assertSanitized(findSecurityEvent(persistedSecurityEvents, "MFA_RECOVERY_PROOF_ISSUED").getUserAgent());
    assertSanitized(
        findSecurityEvent(persistedSecurityEvents, "MFA_REBIND_INITIATED").getUserAgent(),
        bootstrapResult.getRebindToken(),
        bootstrapResult.getEnrollmentToken()
    );
    assertSanitized(
        findSecurityEvent(persistedSecurityEvents, "MFA_REBIND_COMPLETED").getUserAgent(),
        bootstrapResult.getRebindToken(),
        bootstrapResult.getEnrollmentToken()
    );

    assertThat(output)
        .contains("Password recovery email dispatch scheduled")
        .doesNotContain(rawForgotResetToken)
        .doesNotContain(RAW_EMAIL);
    assertSanitized(
        output.toString(),
        rawForgotResetToken,
        RAW_EMAIL,
        resetResult.getMfaRecoveryProof(),
        bootstrapResult.getRebindToken(),
        bootstrapResult.getEnrollmentToken()
    );
  }

  @Test
  void shouldKeepDirectFailureLoggerPathsFreeOfRawSensitiveValues(CapturedOutput output) {
    exerciseOrderCleanupFailurePath();
    exerciseMfaConfirmationFailurePath();
    exerciseMfaNonCriticalFailurePath();

    assertThat(output)
        .contains("Failed to delete partially-created order session during activation rollback")
        .contains("Failed to clear order session TTL during activation rollback")
        .contains("Failed to refund order session rate limit during activation rollback")
        .contains("Failed to invalidate sessions during mfa rebind confirmation")
        .contains("Non-critical MFA recovery side effect failed");
    assertSanitized(output.toString());
  }

  private void exerciseOrderSessionPath(
      AuditLogService capturingAuditLogService,
      SecurityEventService capturingSecurityEventService
  ) {
    OrderSessionRepository orderSessionRepository = org.mockito.Mockito.mock(OrderSessionRepository.class);
    ManualRecoveryQueueEntryRepository manualRecoveryQueueEntryRepository =
        org.mockito.Mockito.mock(ManualRecoveryQueueEntryRepository.class);
    SecurityEventRepository eventRepository = org.mockito.Mockito.mock(SecurityEventRepository.class);
    EntityManager entityManager = org.mockito.Mockito.mock(EntityManager.class);
    OrderSessionRateLimitService rateLimitService = org.mockito.Mockito.mock(OrderSessionRateLimitService.class);
    OrderSessionTtlStore ttlStore = org.mockito.Mockito.mock(OrderSessionTtlStore.class);
    AccountPositionService accountPositionService = org.mockito.Mockito.mock(AccountPositionService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-03-19T00:00:00Z"), ZoneOffset.UTC);
    OrderSessionPersistenceService orderSessionPersistenceService =
        new OrderSessionPersistenceService(
            manualRecoveryQueueEntryRepository,
            orderSessionRepository,
            capturingAuditLogService,
            clock
        );
    ReflectionTestUtils.setField(orderSessionPersistenceService, "entityManager", entityManager);
    OrderSessionService orderSessionService = new OrderSessionService(
        orderSessionRepository,
        org.mockito.Mockito.mock(MemberRepository.class),
        capturingAuditLogService,
        capturingSecurityEventService,
        eventRepository,
        org.mockito.Mockito.mock(SessionOwnershipValidator.class),
        orderSessionPersistenceService,
        rateLimitService,
        org.mockito.Mockito.mock(OrderSessionOtpChallengeService.class),
        ttlStore,
        accountPositionService,
        org.mockito.Mockito.mock(TotpService.class),
        org.mockito.Mockito.mock(TotpReplayGuardService.class),
        clock
    );
    ReflectionTestUtils.setField(orderSessionService, "recentLoginMfaWindow", Duration.ofMinutes(60));
    ReflectionTestUtils.setField(orderSessionService, "autoAuthorizeMaxNotional", BigDecimal.valueOf(500_000));
    ReflectionTestUtils.setField(orderSessionService, "recentSecurityEventWindow", Duration.ofHours(24));

    when(orderSessionRepository.findByClOrdId("CL-PII-ORDER-001")).thenReturn(Optional.empty());
    when(orderSessionRepository.saveAndFlush(any(OrderSession.class))).thenAnswer(invocation -> {
      OrderSession session = invocation.getArgument(0);
      ReflectionTestUtils.setField(session, "id", 2201L);
      return session;
    });
    when(ttlStore.ttl()).thenReturn(Duration.ofMinutes(5));
    when(ttlStore.isActive(any(String.class))).thenReturn(true);
    when(eventRepository.findByMemberId(eq(101L), any(Pageable.class))).thenReturn(List.of());
    when(accountPositionService.getAccountSummary(any()))
        .thenReturn(AccountPositionResult.of(
            202L,
            101L,
            null,
            null,
            null,
            BigDecimal.valueOf(1_000_000),
            "KRW",
            Instant.parse("2026-03-19T00:00:00Z")
        ));

    orderSessionService.createOrderSession(OrderSessionCreateCommand.of(
        101L,
        202L,
        "CL-PII-ORDER-001",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(10000),
        Instant.parse("2026-03-18T23:59:45Z"),
        RAW_CLIENT_IP,
        RAW_CONTEXT,
        RAW_CLIENT_IP,
        RAW_CONTEXT
    ));
  }

  private String exercisePasswordRecoveryForgotPath(AuditLogService capturingAuditLogService) {
    PasswordRecoveryRateLimitService rateLimitService =
        org.mockito.Mockito.mock(PasswordRecoveryRateLimitService.class);
    PasswordRecoveryChallengeProvider challengeProvider =
        org.mockito.Mockito.mock(PasswordRecoveryChallengeService.class);
    PasswordRecoveryTokenService tokenService = org.mockito.Mockito.mock(PasswordRecoveryTokenService.class);
    PasswordRecoveryTimingEqualizer timingEqualizer = org.mockito.Mockito.mock(PasswordRecoveryTimingEqualizer.class);
    MemberRepository memberRepository = org.mockito.Mockito.mock(MemberRepository.class);
    PasswordResetTokenRepository passwordResetTokenRepository =
        org.mockito.Mockito.mock(PasswordResetTokenRepository.class);
    PasswordRecoveryChallengeTelemetryService challengeTelemetryService =
        new PasswordRecoveryChallengeTelemetryService(
            new SimpleMeterRegistry(),
            new org.springframework.mock.env.MockEnvironment()
        );
    PasswordRecoveryMailDispatcher mailDispatcher = new LoggingPasswordRecoveryMailDispatcher();
    Member member = member(601L, "M-PII-PR-601", "recover.user@fixyz.com", false);
    String rawResetToken = "raw-reset-token-123";

    when(rateLimitService.registerForgotAttempt(RAW_CLIENT_IP, "recover.user@fixyz.com"))
        .thenReturn(new PasswordRecoveryRateLimitService.ForgotDecision("email-hash", false));
    when(rateLimitService.tryAcquireForgotCooldown("email-hash")).thenReturn(true);
    when(memberRepository.findByEmailForUpdate("recover.user@fixyz.com")).thenReturn(Optional.of(member));
    when(passwordResetTokenRepository.findActiveByMemberIdForUpdate(member.getId())).thenReturn(List.of());
    when(tokenService.generateRawResetToken()).thenReturn(rawResetToken);
    when(tokenService.candidateCurrentHash(rawResetToken))
        .thenReturn(new PasswordRecoveryTokenService.TokenHash((short) 2, "hashed-reset-token"));

    PasswordRecoveryService passwordRecoveryService = new PasswordRecoveryService(
        new PasswordRecoveryProperties(),
        rateLimitService,
        tokenService,
        List.of(challengeProvider),
        challengeTelemetryService,
        timingEqualizer,
        mailDispatcher,
        Runnable::run,
        memberRepository,
        passwordResetTokenRepository,
        capturingAuditLogService,
        org.mockito.Mockito.mock(PasswordEncoder.class),
        org.mockito.Mockito.mock(ChannelSessionInvalidationService.class),
        org.mockito.Mockito.mock(MfaRecoveryService.class)
    );

    passwordRecoveryService.forgot(
        PasswordForgotCommand.of("recover.user@fixyz.com", null, null, false),
        sensitiveRequest()
    );
    return rawResetToken;
  }

  private PasswordResetContinuationResult exercisePasswordRecoveryResetPath(AuditLogService capturingAuditLogService) {
    PasswordRecoveryRateLimitService rateLimitService =
        org.mockito.Mockito.mock(PasswordRecoveryRateLimitService.class);
    PasswordRecoveryChallengeProvider challengeProvider =
        org.mockito.Mockito.mock(PasswordRecoveryChallengeService.class);
    PasswordRecoveryTokenService tokenService = org.mockito.Mockito.mock(PasswordRecoveryTokenService.class);
    PasswordRecoveryTimingEqualizer timingEqualizer = org.mockito.Mockito.mock(PasswordRecoveryTimingEqualizer.class);
    MemberRepository memberRepository = org.mockito.Mockito.mock(MemberRepository.class);
    PasswordResetTokenRepository passwordResetTokenRepository =
        org.mockito.Mockito.mock(PasswordResetTokenRepository.class);
    PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    ChannelSessionInvalidationService invalidationService =
        org.mockito.Mockito.mock(ChannelSessionInvalidationService.class);
    MfaRecoveryService mfaRecoveryService = org.mockito.Mockito.mock(MfaRecoveryService.class);
    PasswordRecoveryChallengeTelemetryService challengeTelemetryService =
        new PasswordRecoveryChallengeTelemetryService(
            new SimpleMeterRegistry(),
            new org.springframework.mock.env.MockEnvironment()
        );
    Member member = member(602L, "M-PII-PR-602", "reset.user@fixyz.com", true);
    String rawResetToken = "raw-reset-token-456";
    PasswordResetToken resetToken = PasswordResetToken.issueActive(
        member.getId(),
        "hashed-reset-token-456",
        (short) 2,
        Instant.now().minusSeconds(60),
        Instant.now().plusSeconds(600),
        MASKED_CLIENT_IP,
        "ua-hash"
    );
    MockHttpServletRequest request = sensitiveRequest();

    when(tokenService.candidateHashes(rawResetToken))
        .thenReturn(List.of(new PasswordRecoveryTokenService.TokenHash((short) 2, "hashed-reset-token-456")));
    when(passwordResetTokenRepository.findByTokenHashesForUpdate(Set.of("hashed-reset-token-456")))
        .thenReturn(List.of(resetToken));
    when(memberRepository.findByIdForUpdate(member.getId())).thenReturn(Optional.of(member));
    when(passwordEncoder.matches(RAW_PASSWORD, member.getPasswordHash())).thenReturn(false);
    when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("{noop}encoded-password");
    when(mfaRecoveryService.issueRecoveryProofIfEligible(member, request))
        .thenReturn(new MfaRecoveryTokenService.RecoveryProof("proof-token-123", Instant.now().plusSeconds(600)));
    when(mfaRecoveryService.recoveryProofTtlSeconds()).thenReturn(600L);

    PasswordRecoveryService passwordRecoveryService = new PasswordRecoveryService(
        new PasswordRecoveryProperties(),
        rateLimitService,
        tokenService,
        List.of(challengeProvider),
        challengeTelemetryService,
        timingEqualizer,
        org.mockito.Mockito.mock(PasswordRecoveryMailDispatcher.class),
        Runnable::run,
        memberRepository,
        passwordResetTokenRepository,
        capturingAuditLogService,
        passwordEncoder,
        invalidationService,
        mfaRecoveryService
    );

    return passwordRecoveryService.reset(PasswordResetCommand.of(rawResetToken, RAW_PASSWORD), request);
  }

  private void exerciseAdminSessionPath(AuditLogService capturingAuditLogService) {
    MemberRepository memberRepository = org.mockito.Mockito.mock(MemberRepository.class);
    ChannelSessionInvalidationService invalidationService =
        org.mockito.Mockito.mock(ChannelSessionInvalidationService.class);
    Member targetMember = member(501L, "M-PII-501", "target.user@fixyz.com", false);

    when(memberRepository.findByMemberNo("M-PII-501")).thenReturn(Optional.of(targetMember));
    when(invalidationService.invalidateAllSessionsWithCount(eq(targetMember.getEmail()), eq("admin-force-logout")))
        .thenReturn(1);

    AdminMemberSessionService adminMemberSessionService = new AdminMemberSessionService(
        memberRepository,
        invalidationService,
        capturingAuditLogService
    );
    adminMemberSessionService.invalidateMemberSessions(
        "M-PII-501",
        AdminActorContext.of(
            900L,
            "OPS-ADMIN-900",
            "admin@fixyz.com",
            RAW_SESSION_TOKEN,
            RAW_CLIENT_IP,
            RAW_CONTEXT,
            "123e4567-e89b-42d3-a456-426614174299"
        )
    );
  }

  private void exerciseMfaIssueRecoveryPath(
      AuditLogService capturingAuditLogService,
      SecurityEventService capturingSecurityEventService
  ) {
    Member member = member(701L, "M-PII-MFA-701", "mfa.user@fixyz.com", true);
    MfaRecoveryTokenService mfaRecoveryTokenService = org.mockito.Mockito.mock(MfaRecoveryTokenService.class);
    when(mfaRecoveryTokenService.issueRecoveryProof(member))
        .thenReturn(new MfaRecoveryTokenService.RecoveryProof("proof-token", Instant.now().plusSeconds(600)));

    MfaRecoveryService mfaRecoveryService = new MfaRecoveryService(
        org.mockito.Mockito.mock(MemberRepository.class),
        capturingAuditLogService,
        capturingSecurityEventService,
        org.mockito.Mockito.mock(PasswordEncoder.class),
        org.mockito.Mockito.mock(TotpEnrollRateLimitService.class),
        org.mockito.Mockito.mock(OtpVerifyRateLimitService.class),
        org.mockito.Mockito.mock(TotpService.class),
        mfaRecoveryTokenService,
        org.mockito.Mockito.mock(LoginTokenService.class),
        org.mockito.Mockito.mock(ChannelSessionInvalidationService.class)
    );

    mfaRecoveryService.issueRecoveryProofIfEligible(member, sensitiveRequest());
  }

  private TotpRebindBootstrapResult exerciseMfaBootstrapPath(
      AuditLogService capturingAuditLogService,
      SecurityEventService capturingSecurityEventService
  ) {
    MemberRepository memberRepository = org.mockito.Mockito.mock(MemberRepository.class);
    MfaRecoveryTokenService mfaRecoveryTokenService = org.mockito.Mockito.mock(MfaRecoveryTokenService.class);
    LoginTokenService loginTokenService = org.mockito.Mockito.mock(LoginTokenService.class);
    TotpEnrollRateLimitService totpEnrollRateLimitService = org.mockito.Mockito.mock(TotpEnrollRateLimitService.class);
    TotpService totpService = org.mockito.Mockito.mock(TotpService.class);
    Member member = member(702L, "M-PII-MFA-702", "bootstrap.user@fixyz.com", true);
    Instant expiresAt = Instant.now().plusSeconds(600);

    stubWithTokenLock(loginTokenService);
    when(mfaRecoveryTokenService.requireActiveRecoveryProof("proof-token-456"))
        .thenReturn(new MfaRecoveryTokenService.RecoveryProofState("proof-token-456", member.getId(), expiresAt));
    when(memberRepository.findByIdForUpdate(member.getId())).thenReturn(Optional.of(member));
    when(mfaRecoveryTokenService.issueRebindToken(member))
        .thenReturn(new MfaRecoveryTokenService.RebindTokenState("rebind-token-456", member.getId(), expiresAt));
    when(totpService.bootstrap(member, expiresAt, "rebind-token-456"))
        .thenReturn(new TotpService.TotpEnrollment("MANUALKEY123", "otpauth://fixyz", "enroll-token-456", expiresAt));

    MfaRecoveryService mfaRecoveryService = new MfaRecoveryService(
        memberRepository,
        capturingAuditLogService,
        capturingSecurityEventService,
        org.mockito.Mockito.mock(PasswordEncoder.class),
        totpEnrollRateLimitService,
        org.mockito.Mockito.mock(OtpVerifyRateLimitService.class),
        totpService,
        mfaRecoveryTokenService,
        loginTokenService,
        org.mockito.Mockito.mock(ChannelSessionInvalidationService.class)
    );

    return mfaRecoveryService.bootstrapWithRecoveryProof(
        MfaRecoveryRebindCommand.of("proof-token-456"),
        sensitiveRequest()
    );
  }

  private void exerciseMfaConfirmPath(
      AuditLogService capturingAuditLogService,
      SecurityEventService capturingSecurityEventService,
      String rebindToken,
      String enrollmentToken
  ) {
    MemberRepository memberRepository = org.mockito.Mockito.mock(MemberRepository.class);
    MfaRecoveryTokenService mfaRecoveryTokenService = org.mockito.Mockito.mock(MfaRecoveryTokenService.class);
    LoginTokenService loginTokenService = org.mockito.Mockito.mock(LoginTokenService.class);
    OtpVerifyRateLimitService otpVerifyRateLimitService = org.mockito.Mockito.mock(OtpVerifyRateLimitService.class);
    TotpService totpService = org.mockito.Mockito.mock(TotpService.class);
    ChannelSessionInvalidationService invalidationService =
        org.mockito.Mockito.mock(ChannelSessionInvalidationService.class);
    Member member = member(703L, "M-PII-MFA-703", "confirm.user@fixyz.com", true);
    Instant expiresAt = Instant.now().plusSeconds(600);

    stubWithTokenLock(loginTokenService);
    when(mfaRecoveryTokenService.requireActiveRebindToken(rebindToken))
        .thenReturn(new MfaRecoveryTokenService.RebindTokenState(rebindToken, member.getId(), expiresAt));
    when(memberRepository.findByIdForUpdate(member.getId())).thenReturn(Optional.of(member));
    when(totpService.isValidEnrollmentToken(rebindToken, enrollmentToken)).thenReturn(true);
    when(totpService.verifyPendingCode(member, rebindToken, RAW_OTP))
        .thenReturn(new TotpService.TotpVerification(true, 1L, RAW_OTP));

    MfaRecoveryService mfaRecoveryService = new MfaRecoveryService(
        memberRepository,
        capturingAuditLogService,
        capturingSecurityEventService,
        org.mockito.Mockito.mock(PasswordEncoder.class),
        org.mockito.Mockito.mock(TotpEnrollRateLimitService.class),
        otpVerifyRateLimitService,
        totpService,
        mfaRecoveryTokenService,
        loginTokenService,
        invalidationService
    );

    mfaRecoveryService.confirmRebind(
        MfaRecoveryRebindConfirmCommand.of(rebindToken, enrollmentToken, RAW_OTP),
        sensitiveRequest()
    );
  }

  private void exerciseOrderCleanupFailurePath() {
    OrderSessionRepository orderSessionRepository = org.mockito.Mockito.mock(OrderSessionRepository.class);
    ManualRecoveryQueueEntryRepository manualRecoveryQueueEntryRepository =
        org.mockito.Mockito.mock(ManualRecoveryQueueEntryRepository.class);
    SecurityEventRepository eventRepository = org.mockito.Mockito.mock(SecurityEventRepository.class);
    EntityManager entityManager = org.mockito.Mockito.mock(EntityManager.class);
    OrderSessionRateLimitService rateLimitService = org.mockito.Mockito.mock(OrderSessionRateLimitService.class);
    OrderSessionTtlStore ttlStore = org.mockito.Mockito.mock(OrderSessionTtlStore.class);
    AccountPositionService accountPositionService = org.mockito.Mockito.mock(AccountPositionService.class);
    AuditLogService capturingAuditLogService = org.mockito.Mockito.mock(AuditLogService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-03-19T00:00:00Z"), ZoneOffset.UTC);
    OrderSessionPersistenceService persistenceService =
        new OrderSessionPersistenceService(
            manualRecoveryQueueEntryRepository,
            orderSessionRepository,
            capturingAuditLogService,
            clock
        );
    ReflectionTestUtils.setField(persistenceService, "entityManager", entityManager);
    OrderSessionService orderSessionService = new OrderSessionService(
        orderSessionRepository,
        org.mockito.Mockito.mock(MemberRepository.class),
        capturingAuditLogService,
        org.mockito.Mockito.mock(SecurityEventService.class),
        eventRepository,
        org.mockito.Mockito.mock(SessionOwnershipValidator.class),
        persistenceService,
        rateLimitService,
        org.mockito.Mockito.mock(OrderSessionOtpChallengeService.class),
        ttlStore,
        accountPositionService,
        org.mockito.Mockito.mock(TotpService.class),
        org.mockito.Mockito.mock(TotpReplayGuardService.class),
        clock
    );
    ReflectionTestUtils.setField(orderSessionService, "recentLoginMfaWindow", Duration.ofMinutes(60));
    ReflectionTestUtils.setField(orderSessionService, "autoAuthorizeMaxNotional", BigDecimal.valueOf(500_000));
    ReflectionTestUtils.setField(orderSessionService, "recentSecurityEventWindow", Duration.ofHours(24));

    OrderSessionCreateCommand command = OrderSessionCreateCommand.of(
        801L,
        901L,
        "CL-PII-DIRECT-001",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(70000),
        Instant.parse("2026-03-18T23:55:00Z"),
        RAW_CLIENT_IP,
        RAW_CONTEXT,
        RAW_CLIENT_IP,
        RAW_CONTEXT
    );
    OrderSession savedSession = OrderSession.initiated(
        command.getMemberId(),
        command.getAccountId(),
        command.getClOrdId(),
        command.replayFingerprint(),
        command.getSymbol(),
        command.getSide(),
        command.getOrderType(),
        command.getQty(),
        command.getPrice(),
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.now(clock).plus(Duration.ofMinutes(5))
    );

    when(orderSessionRepository.findByClOrdId(command.getClOrdId())).thenReturn(Optional.empty());
    when(orderSessionRepository.saveAndFlush(any(OrderSession.class))).thenReturn(savedSession);
    when(orderSessionRepository.findByOrderSessionId(savedSession.getOrderSessionId())).thenReturn(Optional.of(savedSession));
    when(eventRepository.findByMemberId(eq(command.getMemberId()), any(Pageable.class))).thenReturn(List.of());
    when(accountPositionService.getAccountSummary(any()))
        .thenReturn(AccountPositionResult.of(
            command.getAccountId(),
            command.getMemberId(),
            null,
            null,
            null,
            BigDecimal.valueOf(1_000_000),
            "KRW",
            Instant.now(clock)
        ));
    when(ttlStore.ttl()).thenReturn(Duration.ofMinutes(5));
    doThrow(new RuntimeException(RAW_CONTEXT)).when(ttlStore).activate(eq(savedSession.getOrderSessionId()), any(Instant.class));
    doThrow(new RuntimeException(RAW_CONTEXT)).when(ttlStore).clear(savedSession.getOrderSessionId());
    doThrow(new RuntimeException(RAW_CONTEXT)).when(orderSessionRepository).delete(savedSession);
    doThrow(new RuntimeException(RAW_CONTEXT)).when(rateLimitService).refundCreateRateLimit(command.getMemberId());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderSessionService.createOrderSession(command))
        .isInstanceOf(RuntimeException.class);
  }

  private void exerciseMfaConfirmationFailurePath() {
    MemberRepository memberRepository = org.mockito.Mockito.mock(MemberRepository.class);
    AuditLogService capturingAuditLogService = org.mockito.Mockito.mock(AuditLogService.class);
    SecurityEventService capturingSecurityEventService = org.mockito.Mockito.mock(SecurityEventService.class);
    MfaRecoveryTokenService mfaRecoveryTokenService = org.mockito.Mockito.mock(MfaRecoveryTokenService.class);
    LoginTokenService loginTokenService = org.mockito.Mockito.mock(LoginTokenService.class);
    OtpVerifyRateLimitService otpVerifyRateLimitService = org.mockito.Mockito.mock(OtpVerifyRateLimitService.class);
    TotpService totpService = org.mockito.Mockito.mock(TotpService.class);
    ChannelSessionInvalidationService invalidationService =
        org.mockito.Mockito.mock(ChannelSessionInvalidationService.class);
    Member member = member(901L, "M-PII-MFA-901", "failure.user@fixyz.com", true);
    String rebindToken = "rebind-token-direct-901";
    String enrollmentToken = "enroll-token-direct-901";
    Instant expiresAt = Instant.now().plusSeconds(600);

    stubWithTokenLock(loginTokenService);
    when(mfaRecoveryTokenService.requireActiveRebindToken(rebindToken))
        .thenReturn(new MfaRecoveryTokenService.RebindTokenState(rebindToken, member.getId(), expiresAt));
    when(memberRepository.findByIdForUpdate(member.getId())).thenReturn(Optional.of(member));
    when(totpService.isValidEnrollmentToken(rebindToken, enrollmentToken)).thenReturn(true);
    when(totpService.verifyPendingCode(member, rebindToken, RAW_OTP))
        .thenReturn(new TotpService.TotpVerification(true, 1L, RAW_OTP));
    doThrow(new RuntimeException(RAW_CONTEXT)).when(invalidationService)
        .invalidateAllSessions(member.getEmail(), "mfa-rebind-completed");

    MfaRecoveryService mfaRecoveryService = new MfaRecoveryService(
        memberRepository,
        capturingAuditLogService,
        capturingSecurityEventService,
        org.mockito.Mockito.mock(PasswordEncoder.class),
        org.mockito.Mockito.mock(TotpEnrollRateLimitService.class),
        otpVerifyRateLimitService,
        totpService,
        mfaRecoveryTokenService,
        loginTokenService,
        invalidationService
    );

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> mfaRecoveryService.confirmRebind(
            MfaRecoveryRebindConfirmCommand.of(rebindToken, enrollmentToken, RAW_OTP),
            sensitiveRequest()
        ))
        .hasMessageContaining("Internal server error");
  }

  private void exerciseMfaNonCriticalFailurePath() {
    MemberRepository memberRepository = org.mockito.Mockito.mock(MemberRepository.class);
    AuditLogService capturingAuditLogService = org.mockito.Mockito.mock(AuditLogService.class);
    SecurityEventService capturingSecurityEventService = org.mockito.Mockito.mock(SecurityEventService.class);
    MfaRecoveryTokenService mfaRecoveryTokenService = org.mockito.Mockito.mock(MfaRecoveryTokenService.class);
    LoginTokenService loginTokenService = org.mockito.Mockito.mock(LoginTokenService.class);
    TotpEnrollRateLimitService totpEnrollRateLimitService = org.mockito.Mockito.mock(TotpEnrollRateLimitService.class);
    TotpService totpService = org.mockito.Mockito.mock(TotpService.class);
    Member member = member(902L, "M-PII-MFA-902", "noncritical.user@fixyz.com", true);
    Instant expiresAt = Instant.now().plusSeconds(600);

    stubWithTokenLock(loginTokenService);
    when(mfaRecoveryTokenService.requireActiveRecoveryProof("proof-token-direct-902"))
        .thenReturn(new MfaRecoveryTokenService.RecoveryProofState("proof-token-direct-902", member.getId(), expiresAt));
    when(memberRepository.findByIdForUpdate(member.getId())).thenReturn(Optional.of(member));
    when(mfaRecoveryTokenService.issueRebindToken(member))
        .thenReturn(new MfaRecoveryTokenService.RebindTokenState("rebind-token-direct-902", member.getId(), expiresAt));
    when(totpService.bootstrap(member, expiresAt, "rebind-token-direct-902"))
        .thenReturn(new TotpService.TotpEnrollment("MANUALKEY902", "otpauth://fixyz/902", "enroll-token-direct-902", expiresAt));
    doThrow(new RuntimeException(RAW_CONTEXT)).when(capturingAuditLogService).record(any(AuditLog.class));

    MfaRecoveryService mfaRecoveryService = new MfaRecoveryService(
        memberRepository,
        capturingAuditLogService,
        capturingSecurityEventService,
        org.mockito.Mockito.mock(PasswordEncoder.class),
        totpEnrollRateLimitService,
        org.mockito.Mockito.mock(OtpVerifyRateLimitService.class),
        totpService,
        mfaRecoveryTokenService,
        loginTokenService,
        org.mockito.Mockito.mock(ChannelSessionInvalidationService.class)
    );

    mfaRecoveryService.bootstrapWithRecoveryProof(
        MfaRecoveryRebindCommand.of("proof-token-direct-902"),
        sensitiveRequest()
    );
  }

  private static void stubWithTokenLock(LoginTokenService loginTokenService) {
    when(loginTokenService.withTokenLock(any(), any())).thenAnswer(invocation -> {
      Supplier<?> action = invocation.getArgument(1);
      return action.get();
    });
  }

  private static AuditLog findAudit(List<AuditLog> auditLogs, String action) {
    return auditLogs.stream()
        .filter(auditLog -> action.equals(auditLog.getAction()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing audit log action: " + action));
  }

  private static AuditLog findAudit(List<AuditLog> auditLogs, String action, String targetType) {
    return auditLogs.stream()
        .filter(auditLog -> action.equals(auditLog.getAction()))
        .filter(auditLog -> targetType.equals(auditLog.getTargetType()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing audit log action/targetType: " + action + "/" + targetType));
  }

  private static SecurityEvent findSecurityEvent(List<SecurityEvent> securityEvents, String eventType) {
    return securityEvents.stream()
        .filter(securityEvent -> eventType.equals(securityEvent.getEventType()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing security event type: " + eventType));
  }

  private static Member member(Long memberId, String memberNo, String email, boolean totpEnabled) {
    Member member = Member.registerUser(memberNo, email, "{noop}password", "PII Test User");
    ReflectionTestUtils.setField(member, "id", memberId);
    if (totpEnabled) {
      member.enableTotpEnrollment();
    }
    return member;
  }

  private static MockHttpServletRequest sensitiveRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(RAW_CLIENT_IP);
    request.addHeader("User-Agent", RAW_CONTEXT);
    request.addHeader("X-Correlation-Id", "123e4567-e89b-42d3-a456-426614174299");
    return request;
  }

  private AuditLog captureAuditLog(AuditLog auditLog) {
    ReflectionTestUtils.setField(auditLog, "id", auditIdSequence.getAndIncrement());
    assertAuditFitsPersistenceContract(auditLog);
    persistedAuditLogs.add(auditLog);
    return auditLog;
  }

  private SecurityEvent captureSecurityEvent(SecurityEvent securityEvent) {
    ReflectionTestUtils.setField(securityEvent, "id", securityEventIdSequence.getAndIncrement());
    assertSecurityEventFitsPersistenceContract(securityEvent);
    persistedSecurityEvents.add(securityEvent);
    return securityEvent;
  }

  private static void assertAuditFitsPersistenceContract(AuditLog auditLog) {
    assertThat(lengthOf(auditLog.getTargetId())).isLessThanOrEqualTo(100);
    assertThat(lengthOf(auditLog.getDetail())).isLessThanOrEqualTo(1000);
    assertThat(lengthOf(auditLog.getIpAddress())).isLessThanOrEqualTo(45);
    assertThat(lengthOf(auditLog.getUserAgent())).isLessThanOrEqualTo(1000);
  }

  private static void assertSecurityEventFitsPersistenceContract(SecurityEvent securityEvent) {
    assertThat(lengthOf(securityEvent.getIpAddress())).isLessThanOrEqualTo(45);
    assertThat(lengthOf(securityEvent.getUserAgent())).isLessThanOrEqualTo(255);
    assertThat(lengthOf(securityEvent.getDetail())).isLessThanOrEqualTo(2000);
  }

  private static int lengthOf(String value) {
    return value == null ? 0 : value.length();
  }

  private static void assertSanitized(String value, String... extraSecrets) {
    assertThat(value)
        .doesNotContain(RAW_ACCOUNT_NUMBER)
        .doesNotContain(RAW_PASSWORD)
        .doesNotContain(RAW_OTP)
        .doesNotContain(RAW_SESSION_TOKEN)
        .doesNotContain(RAW_AUTHORIZATION)
        .doesNotContain(RAW_LEGACY_SESSION)
        .doesNotContain(RAW_GENERIC_TOKEN)
        .doesNotContain(RAW_EMAIL)
        .doesNotContain(RAW_CLIENT_IP);
    for (String extraSecret : extraSecrets) {
      if (extraSecret != null && !extraSecret.isBlank()) {
        assertThat(value).doesNotContain(extraSecret);
      }
    }
  }
}
