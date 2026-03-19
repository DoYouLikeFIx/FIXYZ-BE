package com.fix.channel.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Member;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.SecurityEvent;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.ManualRecoveryQueueEntryRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.service.AccountPositionService;
import com.fix.channel.service.AuditLogService;
import com.fix.channel.service.ChannelSessionInvalidationService;
import com.fix.channel.service.LoginTokenService;
import com.fix.channel.service.MfaRecoveryService;
import com.fix.channel.service.MfaRecoveryTokenService;
import com.fix.channel.service.OtpVerifyRateLimitService;
import com.fix.channel.service.OrderSessionOtpChallengeService;
import com.fix.channel.service.OrderSessionPersistenceService;
import com.fix.channel.service.OrderSessionRateLimitService;
import com.fix.channel.service.OrderSessionService;
import com.fix.channel.service.OrderSessionTtlStore;
import com.fix.channel.service.SecurityEventService;
import com.fix.channel.service.SessionOwnershipValidator;
import com.fix.channel.service.TotpEnrollRateLimitService;
import com.fix.channel.service.TotpReplayGuardService;
import com.fix.channel.service.TotpService;
import com.fix.channel.vo.AccountPositionResult;
import com.fix.channel.vo.MfaRecoveryRebindCommand;
import com.fix.channel.vo.MfaRecoveryRebindConfirmCommand;
import com.fix.channel.vo.OrderSessionCreateCommand;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

  private AuditLogService auditLogService;
  private SecurityEventService securityEventService;

  @BeforeEach
  void setUp() {
    lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
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
        .isInstanceOf(RuntimeException.class);
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

  private static void assertSanitized(String value) {
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
  }
}
