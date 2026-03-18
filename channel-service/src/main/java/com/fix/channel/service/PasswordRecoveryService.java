package com.fix.channel.service;

import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Member;
import com.fix.channel.entity.PasswordResetToken;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.PasswordResetTokenRepository;
import com.fix.channel.support.ChannelCorrelationIdSupport;
import com.fix.channel.vo.PasswordForgotChallengeCommand;
import com.fix.channel.vo.PasswordForgotChallengeResult;
import com.fix.channel.vo.PasswordForgotCommand;
import com.fix.channel.vo.PasswordForgotResult;
import com.fix.channel.vo.PasswordResetContinuationResult;
import com.fix.channel.vo.PasswordResetCommand;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.web.CommonHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PasswordRecoveryService {

  private static final String FORGOT_MESSAGE = "If the account is eligible, a reset email will be sent.";
  private static final String CHALLENGE_ENDPOINT = "/api/v1/auth/password/forgot/challenge";

  private final PasswordRecoveryProperties properties;
  private final PasswordRecoveryRateLimitService rateLimitService;
  private final PasswordRecoveryTokenService tokenService;
  private final PasswordRecoveryChallengeService challengeService;
  private final PasswordRecoveryTimingEqualizer timingEqualizer;
  private final PasswordRecoveryMailDispatcher mailDispatcher;
  private final TaskExecutor taskExecutor;
  private final MemberRepository memberRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final AuditLogService auditLogService;
  private final PasswordEncoder passwordEncoder;
  private final ChannelSessionInvalidationService channelSessionInvalidationService;
  private final MfaRecoveryService mfaRecoveryService;

  public PasswordRecoveryService(
      PasswordRecoveryProperties properties,
      PasswordRecoveryRateLimitService rateLimitService,
      PasswordRecoveryTokenService tokenService,
      PasswordRecoveryChallengeService challengeService,
      PasswordRecoveryTimingEqualizer timingEqualizer,
      PasswordRecoveryMailDispatcher mailDispatcher,
      @Qualifier("passwordRecoveryTaskExecutor") TaskExecutor taskExecutor,
      MemberRepository memberRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      AuditLogService auditLogService,
      PasswordEncoder passwordEncoder,
      ChannelSessionInvalidationService channelSessionInvalidationService,
      MfaRecoveryService mfaRecoveryService
  ) {
    this.properties = properties;
    this.rateLimitService = rateLimitService;
    this.tokenService = tokenService;
    this.challengeService = challengeService;
    this.timingEqualizer = timingEqualizer;
    this.mailDispatcher = mailDispatcher;
    this.taskExecutor = taskExecutor;
    this.memberRepository = memberRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.auditLogService = auditLogService;
    this.passwordEncoder = passwordEncoder;
    this.channelSessionInvalidationService = channelSessionInvalidationService;
    this.mfaRecoveryService = mfaRecoveryService;
  }

  @Transactional
  public PasswordForgotResult forgot(PasswordForgotCommand command, HttpServletRequest request) {
    long startedAt = timingEqualizer.start();
    try {
      String normalizedEmail = normalizeEmail(command.getEmail());
      String clientIp = resolveClientIp(request);
      String userAgent = resolveUserAgent(request);
      String correlationId = resolveCorrelationId(request);

      PasswordRecoveryRateLimitService.ForgotDecision forgotDecision =
          rateLimitService.registerForgotAttempt(clientIp, normalizedEmail);

      boolean challengeProvided = hasText(command.getChallengeToken()) || hasText(command.getChallengeAnswer());
      if (challengeProvided) {
        challengeService.validateAndConsume(
            normalizedEmail,
            command.getChallengeToken(),
            command.getChallengeAnswer()
        );
      }

      Member member = memberRepository.findByEmailForUpdate(normalizedEmail).orElse(null);
      boolean challengeSatisfied = !forgotDecision.challengeRequired() || challengeProvided;
      boolean cooldownAvailable = rateLimitService.tryAcquireForgotCooldown(forgotDecision.emailHash());

      if (member != null && challengeSatisfied && cooldownAvailable) {
        issueResetToken(member, clientIp, userAgent, correlationId);
        auditLogService.record(AuditLog.of(
            member.getId(),
            AuditAction.PASSWORD_RECOVERY_FORGOT,
            "MEMBER",
            String.valueOf(member.getId()),
            "password recovery accepted",
            clientIp,
            userAgent,
            correlationId
        ));
      } else {
        auditLogService.record(AuditLog.of(
            member == null ? null : member.getId(),
            AuditAction.PASSWORD_RECOVERY_FORGOT,
            "MEMBER",
            member == null ? null : String.valueOf(member.getId()),
            "password recovery accepted",
            clientIp,
            userAgent,
            correlationId
        ));
      }

      return PasswordForgotResult.accepted(FORGOT_MESSAGE, CHALLENGE_ENDPOINT, true);
    } finally {
      timingEqualizer.equalizeForgot(startedAt);
    }
  }

  public PasswordForgotChallengeResult bootstrapChallenge(
      PasswordForgotChallengeCommand command,
      HttpServletRequest request
  ) {
    String normalizedEmail = normalizeEmail(command.getEmail());
    String clientIp = resolveClientIp(request);
    String userAgent = resolveUserAgent(request);
    String correlationId = resolveCorrelationId(request);

    rateLimitService.registerChallengeAttempt(clientIp, normalizedEmail);
    PasswordRecoveryChallengeService.ChallengePayload payload = challengeService.issue(normalizedEmail);

    auditLogService.record(AuditLog.of(
        null,
        AuditAction.PASSWORD_RECOVERY_CHALLENGE_ISSUED,
        "PASSWORD_RECOVERY",
        null,
        "challenge issued",
        clientIp,
        userAgent,
        correlationId
    ));

    return PasswordForgotChallengeResult.of(
        payload.token(),
        payload.type(),
        properties.getChallenge().getTtlSeconds()
    );
  }

  @Transactional
  public PasswordResetContinuationResult reset(PasswordResetCommand command, HttpServletRequest request) {
    long startedAt = timingEqualizer.start();
    try {
      String clientIp = resolveClientIp(request);
      String userAgent = resolveUserAgent(request);
      String correlationId = resolveCorrelationId(request);
      rateLimitService.registerResetAttempt(clientIp, command.getToken());

      List<PasswordRecoveryTokenService.TokenHash> candidateHashes = tokenService.candidateHashes(command.getToken());
      Map<String, PasswordRecoveryTokenService.TokenHash> hashLookup = candidateHashes.stream()
          .collect(Collectors.toMap(PasswordRecoveryTokenService.TokenHash::hash, candidate -> candidate, (left, right) -> left));
      Set<String> hashes = hashLookup.keySet();

      PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByTokenHashesForUpdate(hashes).stream()
          .sorted(Comparator.comparing(PasswordResetToken::getIssuedAt).reversed())
          .findFirst()
          .orElseThrow(() -> new BusinessException(
              ErrorCode.AUTH_RESET_TOKEN_INVALID,
              "reset token invalid or expired"
          ));

      Instant now = Instant.now();
      if (passwordResetToken.isConsumed()) {
        throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_CONSUMED, "reset token already consumed");
      }
      if (!passwordResetToken.isActive()) {
        throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "reset token invalid or expired");
      }
      if (passwordResetToken.isExpiredAt(now)) {
        throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "reset token invalid or expired");
      }

      PasswordRecoveryTokenService.TokenHash matchedHash = hashLookup.get(passwordResetToken.getTokenHash());
      if (matchedHash == null || passwordResetToken.getPepperVersion() != matchedHash.pepperVersion()) {
        throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "reset token invalid or expired");
      }

      Member member = memberRepository.findByIdForUpdate(passwordResetToken.getMemberId())
          .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "reset token invalid or expired"));

      if (passwordEncoder.matches(command.getNewPassword(), member.getPasswordHash())) {
        throw new BusinessException(
            ErrorCode.AUTH_PASSWORD_REUSE_FORBIDDEN,
            "new password equals current password"
        );
      }

      member.updatePasswordHash(passwordEncoder.encode(command.getNewPassword()));
      member.activate();
      passwordResetToken.consume(now);

      auditLogService.record(AuditLog.of(
          member.getId(),
          AuditAction.PASSWORD_RECOVERY_RESET,
          "MEMBER",
          String.valueOf(member.getId()),
          "password reset completed",
          clientIp,
          userAgent,
          correlationId
      ));

      MfaRecoveryTokenService.RecoveryProof recoveryProof =
          mfaRecoveryService.issueRecoveryProofIfEligible(member, request);
      registerAfterCommit(() -> channelSessionInvalidationService.invalidateAllPasswordSessions(member.getEmail()));
      if (recoveryProof != null) {
        return PasswordResetContinuationResult.withRecoveryProof(
            recoveryProof.recoveryProof(),
            mfaRecoveryService.recoveryProofTtlSeconds()
        );
      }
      return PasswordResetContinuationResult.none();
    } finally {
      timingEqualizer.equalizeReset(startedAt);
    }
  }

  private void issueResetToken(Member member, String clientIp, String userAgent, String correlationId) {
    Instant issuedAt = Instant.now();
    passwordResetTokenRepository.findActiveByMemberIdForUpdate(member.getId())
        .forEach(token -> token.supersede(issuedAt));
    passwordResetTokenRepository.flush();

    String rawToken = tokenService.generateRawResetToken();
    PasswordRecoveryTokenService.TokenHash currentHash = tokenService.candidateCurrentHash(rawToken);
    Instant expiresAt = issuedAt.plus(properties.getReset().getTokenTtl());

    passwordResetTokenRepository.save(PasswordResetToken.issueActive(
        member.getId(),
        currentHash.hash(),
        currentHash.pepperVersion(),
        issuedAt,
        expiresAt,
        maskIpAddress(clientIp),
        tokenService.fingerprint(userAgent)
    ));

    registerAfterCommit(() -> taskExecutor.execute(() -> mailDispatcher.dispatch(member.getEmail(), rawToken, expiresAt)));

    auditLogService.record(AuditLog.of(
        member.getId(),
        AuditAction.PASSWORD_RECOVERY_FORGOT,
        "PASSWORD_RECOVERY",
        String.valueOf(member.getId()),
        "reset token issued",
        clientIp,
        userAgent,
        correlationId
    ));
  }

  private void registerAfterCommit(Runnable runnable) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      runnable.run();
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        runnable.run();
      }
    });
  }

  private String normalizeEmail(String email) {
    return Normalizer.normalize(email == null ? "" : email.trim(), Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String maskIpAddress(String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return null;
    }
    if (clientIp.contains(".")) {
      String[] octets = clientIp.split("\\.");
      if (octets.length == 4) {
        return octets[0] + "." + octets[1] + "." + octets[2] + ".0";
      }
    }
    if (clientIp.contains(":")) {
      int lastColon = clientIp.lastIndexOf(':');
      if (lastColon > 0) {
        return clientIp.substring(0, lastColon) + ":0";
      }
    }
    return clientIp;
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
}
