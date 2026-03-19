package com.fix.channel.service;

import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Member;
import com.fix.channel.entity.SecurityEvent;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.support.ChannelCorrelationIdSupport;
import com.fix.channel.vo.MemberTotpRebindCommand;
import com.fix.channel.vo.MfaRecoveryRebindCommand;
import com.fix.channel.vo.MfaRecoveryRebindConfirmCommand;
import com.fix.channel.vo.MfaRecoveryRebindConfirmResult;
import com.fix.channel.vo.TotpRebindBootstrapResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.logging.LogPiiMasking;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaRecoveryService {

  private static final Logger log = LoggerFactory.getLogger(MfaRecoveryService.class);

  private static final String SECURITY_EVENT_PROOF_ISSUED = "MFA_RECOVERY_PROOF_ISSUED";
  private static final String SECURITY_EVENT_REBIND_INITIATED = "MFA_REBIND_INITIATED";
  private static final String SECURITY_EVENT_REBIND_COMPLETED = "MFA_REBIND_COMPLETED";
  private static final String SECURITY_EVENT_REBIND_FAILED = "MFA_REBIND_FAILED";

  private final MemberRepository memberRepository;
  private final AuditLogService auditLogService;
  private final SecurityEventService securityEventService;
  private final PasswordEncoder passwordEncoder;
  private final TotpEnrollRateLimitService totpEnrollRateLimitService;
  private final OtpVerifyRateLimitService otpVerifyRateLimitService;
  private final TotpService totpService;
  private final MfaRecoveryTokenService mfaRecoveryTokenService;
  private final LoginTokenService loginTokenService;
  private final ChannelSessionInvalidationService channelSessionInvalidationService;

  public MfaRecoveryService(
      MemberRepository memberRepository,
      AuditLogService auditLogService,
      SecurityEventService securityEventService,
      PasswordEncoder passwordEncoder,
      TotpEnrollRateLimitService totpEnrollRateLimitService,
      OtpVerifyRateLimitService otpVerifyRateLimitService,
      TotpService totpService,
      MfaRecoveryTokenService mfaRecoveryTokenService,
      LoginTokenService loginTokenService,
      ChannelSessionInvalidationService channelSessionInvalidationService
  ) {
    this.memberRepository = memberRepository;
    this.auditLogService = auditLogService;
    this.securityEventService = securityEventService;
    this.passwordEncoder = passwordEncoder;
    this.totpEnrollRateLimitService = totpEnrollRateLimitService;
    this.otpVerifyRateLimitService = otpVerifyRateLimitService;
    this.totpService = totpService;
    this.mfaRecoveryTokenService = mfaRecoveryTokenService;
    this.loginTokenService = loginTokenService;
    this.channelSessionInvalidationService = channelSessionInvalidationService;
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public TotpRebindBootstrapResult bootstrapAuthenticated(
      MemberTotpRebindCommand command,
      HttpServletRequest request
  ) {
    Member member = requireAuthenticatedMemberForUpdate(request);
    if (!member.isTotpEnabled()) {
      throw new BusinessException(
          ErrorCode.AUTH_TOTP_ENROLLMENT_REQUIRED,
          "totp enrollment required",
          new ErrorMetadata(null, null, Map.of("enrollUrl", "/settings/totp/enroll"))
      );
    }
    if (!passwordEncoder.matches(command.getCurrentPassword(), member.getPasswordHash())) {
      throw new BusinessException(
          ErrorCode.AUTH_MFA_REBIND_CURRENT_PASSWORD_MISMATCH,
          "current password mismatch"
      );
    }

    return loginTokenService.withTokenLock(memberLockKey(member.getId()), () ->
        bootstrapForMember(member, "authenticated", request)
    );
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public TotpRebindBootstrapResult bootstrapWithRecoveryProof(
      MfaRecoveryRebindCommand command,
      HttpServletRequest request
  ) {
    String clientIp = resolveClientIp(request);
    String userAgent = resolveUserAgent(request);
    String correlationId = resolveCorrelationId(request);
    Long memberId = null;
    try {
      MfaRecoveryTokenService.RecoveryProofState recoveryProofState =
          mfaRecoveryTokenService.requireActiveRecoveryProof(command.getRecoveryProof());
      memberId = recoveryProofState.memberId();
      Long lockedMemberId = memberId;
      return loginTokenService.withTokenLock(memberLockKey(lockedMemberId), () -> {
        mfaRecoveryTokenService.requireActiveRecoveryProof(command.getRecoveryProof());

        Member member = memberRepository.findByIdForUpdate(lockedMemberId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.AUTH_MFA_RECOVERY_TOKEN_INVALID,
                "mfa recovery proof or rebind token invalid or expired"
            ));
        if (!member.isTotpEnabled()) {
          throw new BusinessException(
              ErrorCode.AUTH_MFA_RECOVERY_TOKEN_INVALID,
              "mfa recovery proof or rebind token invalid or expired"
          );
        }
        TotpRebindBootstrapResult result = bootstrapForMember(member, "recovery-proof", request);
        mfaRecoveryTokenService.consumeRecoveryProof(command.getRecoveryProof());
        return result;
      });
    } catch (BusinessException ex) {
      recordFailure(memberId, ex.getErrorCode().code(), clientIp, userAgent, correlationId);
      throw ex;
    }
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public MfaRecoveryRebindConfirmResult confirmRebind(
      MfaRecoveryRebindConfirmCommand command,
      HttpServletRequest request
  ) {
    return loginTokenService.withTokenLock(command.getRebindToken(), () -> doConfirmRebind(command, request));
  }

  public MfaRecoveryTokenService.RecoveryProof issueRecoveryProofIfEligible(Member member, HttpServletRequest request) {
    if (!member.isTotpEnabled()) {
      return null;
    }

    MfaRecoveryTokenService.RecoveryProof recoveryProof = mfaRecoveryTokenService.issueRecoveryProof(member);
    String clientIp = resolveClientIp(request);
    String userAgent = resolveUserAgent(request);
    String correlationId = resolveCorrelationId(request);

    auditLogService.record(AuditLog.of(
        member.getId(),
        AuditAction.AUTH_MFA_RECOVERY_PROOF_ISSUED,
        "MFA_RECOVERY",
        String.valueOf(member.getId()),
        "password reset issued mfa recovery continuation",
        clientIp,
        userAgent,
        correlationId
    ));
    securityEventService.record(SecurityEvent.of(
        member.getId(),
        SECURITY_EVENT_PROOF_ISSUED,
        clientIp,
        userAgent,
        "HIGH"
    ).withCorrelationId(correlationId).withDetail("reason=password_reset_continuation"));
    return recoveryProof;
  }

  public long recoveryProofTtlSeconds() {
    return mfaRecoveryTokenService.recoveryProofTtlSeconds();
  }

  private TotpRebindBootstrapResult bootstrapForMember(Member member, String source, HttpServletRequest request) {
    String clientIp = resolveClientIp(request);
    String userAgent = resolveUserAgent(request);
    String correlationId = resolveCorrelationId(request);
    String rateLimitKey = "mfa-rebind:" + member.getId();

    totpEnrollRateLimitService.checkAllowed(rateLimitKey);
    totpEnrollRateLimitService.recordAttempt(rateLimitKey);

    MfaRecoveryTokenService.RebindTokenState rebindTokenState = null;
    try {
      rebindTokenState = mfaRecoveryTokenService.issueRebindToken(member);
      TotpService.TotpEnrollment enrollment = totpService.bootstrap(
          member,
          rebindTokenState.expiresAt(),
          rebindTokenState.rebindToken()
      );
      totpService.terminalizeActiveSecret(member);
      persistBootstrapEvents(member, source, clientIp, userAgent, correlationId);

      return TotpRebindBootstrapResult.of(
          rebindTokenState.rebindToken(),
          enrollment.manualEntryKey(),
          enrollment.qrUri(),
          enrollment.enrollmentToken(),
          enrollment.expiresAt()
      );
    } catch (RuntimeException ex) {
      cleanupFailedBootstrap(member, rebindTokenState);
      throw ex;
    }
  }

  private MfaRecoveryRebindConfirmResult doConfirmRebind(
      MfaRecoveryRebindConfirmCommand command,
      HttpServletRequest request
  ) {
    String clientIp = resolveClientIp(request);
    String userAgent = resolveUserAgent(request);
    String correlationId = resolveCorrelationId(request);
    Long memberId = null;

    try {
      MfaRecoveryTokenService.RebindTokenState rebindTokenState =
          mfaRecoveryTokenService.requireActiveRebindToken(command.getRebindToken());
      memberId = rebindTokenState.memberId();
      otpVerifyRateLimitService.checkAllowed(command.getRebindToken());

      Member member = memberRepository.findByIdForUpdate(memberId)
          .orElseThrow(() -> new BusinessException(
              ErrorCode.AUTH_MFA_RECOVERY_TOKEN_INVALID,
              "mfa recovery proof or rebind token invalid or expired"
          ));
      if (!totpService.isValidEnrollmentToken(command.getRebindToken(), command.getEnrollmentToken())) {
        throw new BusinessException(
            ErrorCode.AUTH_MFA_RECOVERY_TOKEN_INVALID,
            "mfa recovery proof or rebind token invalid or expired"
        );
      }

      TotpService.TotpVerification verification = totpService.verifyPendingCode(member, command.getRebindToken(), command.getOtpCode());
      if (!verification.matched()) {
        otpVerifyRateLimitService.recordFailure(command.getRebindToken());
        throw new BusinessException(ErrorCode.AUTH_OTP_INVALID, "otp code mismatch");
      }

      otpVerifyRateLimitService.clear(command.getRebindToken());
      try {
        channelSessionInvalidationService.invalidateAllSessions(
            member.getEmail(),
            "mfa-rebind-completed"
        );
      } catch (RuntimeException ex) {
        log.warn(
            "Failed to invalidate sessions during mfa rebind confirmation memberId={} failure={}",
            memberId,
            LogPiiMasking.sanitizeExceptionSummary(ex)
        );
        throw new BusinessException(
            ErrorCode.INTERNAL_ERROR,
            ErrorCode.INTERNAL_ERROR.defaultMessage(),
            ex
        );
      }
      totpService.promotePendingSecret(member, command.getRebindToken());
      member.enableTotpEnrollment();
      mfaRecoveryTokenService.consumeRebindToken(command.getRebindToken());
      persistCompletedEvents(member, clientIp, userAgent, correlationId);
      return MfaRecoveryRebindConfirmResult.completed();
    } catch (BusinessException ex) {
      recordFailure(memberId, ex.getErrorCode().code(), clientIp, userAgent, correlationId);
      throw ex;
    }
  }

  private Member requireAuthenticatedMemberForUpdate(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }

    Object memberIdAttr = session.getAttribute("AUTH_MEMBER_ID");
    if (!(memberIdAttr instanceof Number memberIdNumber)) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }

    Long memberId = memberIdNumber.longValue();
    return memberRepository.findByIdForUpdate(memberId)
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required"));
  }

  private void recordFailure(
      Long memberId,
      String reason,
      String clientIp,
      String userAgent,
      String correlationId
  ) {
    persistNonCritical(() -> {
      auditLogService.record(AuditLog.of(
          memberId,
          AuditAction.AUTH_TOTP_REBIND_FAILED,
          "TOTP",
          memberId == null ? null : String.valueOf(memberId),
          "reason=" + reason,
          clientIp,
          userAgent,
          correlationId
      ));
      securityEventService.record(SecurityEvent.of(
          memberId,
          SECURITY_EVENT_REBIND_FAILED,
          clientIp,
          userAgent,
          "MEDIUM"
      ).withCorrelationId(correlationId).withDetail("reason=" + reason));
    }, "recording MFA rebind failure");
  }

  private void persistBootstrapEvents(
      Member member,
      String source,
      String clientIp,
      String userAgent,
      String correlationId
  ) {
    persistNonCritical(() -> {
      auditLogService.record(AuditLog.of(
          member.getId(),
          AuditAction.AUTH_TOTP_SECRET_TERMINALIZED,
          "TOTP",
          member.getMemberNo(),
          "reason=mfa-rebind-bootstrap",
          clientIp,
          userAgent,
          correlationId
      ));
      auditLogService.record(AuditLog.of(
          member.getId(),
          AuditAction.AUTH_TOTP_REBIND_INITIATED,
          "TOTP",
          member.getMemberNo(),
          "source=" + source,
          clientIp,
          userAgent,
          correlationId
      ));
      securityEventService.record(SecurityEvent.of(
          member.getId(),
          SECURITY_EVENT_REBIND_INITIATED,
          clientIp,
          userAgent,
          "HIGH"
      ).withCorrelationId(correlationId).withDetail("source=" + source));
    }, "recording MFA rebind bootstrap events");
  }

  private void persistCompletedEvents(
      Member member,
      String clientIp,
      String userAgent,
      String correlationId
  ) {
    persistNonCritical(() -> {
      auditLogService.record(AuditLog.of(
          member.getId(),
          AuditAction.AUTH_TOTP_REBIND_CONFIRMED,
          "TOTP",
          member.getMemberNo(),
          "source=mfa-recovery-confirm",
          clientIp,
          userAgent,
          correlationId
      ));
      securityEventService.record(SecurityEvent.of(
          member.getId(),
          SECURITY_EVENT_REBIND_COMPLETED,
          clientIp,
          userAgent,
          "HIGH"
      ).withCorrelationId(correlationId).withDetail("source=mfa-recovery-confirm"));
    }, "recording MFA rebind completion events");
  }

  private void cleanupFailedBootstrap(
      Member member,
      MfaRecoveryTokenService.RebindTokenState rebindTokenState
  ) {
    if (rebindTokenState == null) {
      return;
    }

    persistNonCritical(() -> {
      totpService.discardPendingSecret(member, rebindTokenState.rebindToken());
      mfaRecoveryTokenService.discardRebindToken(rebindTokenState.rebindToken());
    }, "cleaning up failed MFA rebind bootstrap");
  }

  private void persistNonCritical(Runnable runnable, String description) {
    try {
      runnable.run();
    } catch (RuntimeException ex) {
      log.warn(
          "Non-critical MFA recovery side effect failed: {} failure={}",
          LogPiiMasking.sanitizeText(description),
          LogPiiMasking.sanitizeExceptionSummary(ex)
      );
    }
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String resolveUserAgent(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    if (userAgent == null || userAgent.isBlank()) {
      return "unknown";
    }
    return userAgent;
  }

  private String resolveCorrelationId(HttpServletRequest request) {
    return ChannelCorrelationIdSupport.ensureCorrelationId(request);
  }

  private String memberLockKey(Long memberId) {
    return "mfa-rebind-member:" + memberId;
  }
}
