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
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
  private static final String SESSION_PENDING_CHALLENGE_TELEMETRY_CONTEXTS_ATTRIBUTE =
      PasswordRecoveryService.class.getName() + ".challenge.pendingTelemetryContexts";
  private static final String CHALLENGE_ID_HASH_ALGORITHM = "HmacSHA256";
  private static final int CHALLENGE_ID_HASH_LENGTH = 24;
  private static final int MAX_PENDING_CHALLENGE_TELEMETRY_CONTEXTS = 6;
  private static final String DETERMINISTIC_OVERRIDE_V2 = "v2";
  private static final String DETERMINISTIC_OVERRIDE_LEGACY_V1 = "legacy-v1";
  private static final String DETERMINISTIC_OVERRIDE_DISABLED = "disabled";

  private final PasswordRecoveryProperties properties;
  private final PasswordRecoveryRateLimitService rateLimitService;
  private final PasswordRecoveryTokenService tokenService;
  private final List<PasswordRecoveryChallengeProvider> challengeProviders;
  private final PasswordRecoveryChallengeTelemetryService challengeTelemetryService;
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
      List<PasswordRecoveryChallengeProvider> challengeProviders,
      PasswordRecoveryChallengeTelemetryService challengeTelemetryService,
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
    this.challengeProviders = challengeProviders;
    this.challengeTelemetryService = challengeTelemetryService;
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

      boolean challengeProvided = hasText(command.getChallengeToken())
          || hasText(command.getChallengeAnswer())
          || command.isChallengeAnswerPayloadPresent();
      ChallengeRoutingDecision routingDecision = resolveChallengeRoutingDecision(normalizedEmail, request);
      if (challengeProvided) {
        PasswordRecoveryChallengeProvider.ChallengeEventContext challengeContext =
            challengeEventContext("unknown", routingDecision);
        PasswordRecoveryChallengeProvider challengeProvider = null;
        try {
          challengeProvider = resolveChallengeProvider(command.getChallengeToken());
          challengeContext = challengeProvider.describeVerifyContext(
              command.getChallengeToken(),
              challengeEventContext(challengeProvider.challengeContractVersionLabel(), routingDecision)
          );
          String challengeIdHash = challengeIdHash(challengeProvider.extractChallengeId(command.getChallengeToken()));
          if (command.isChallengeAnswerPayloadPresent()) {
            throw new BusinessException(
                ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
                "recovery challenge token is invalid or expired"
            );
          }
          challengeProvider.validate(
              command.getEmail(),
              normalizedEmail,
              command.getChallengeToken(),
              command.getChallengeAnswer(),
              request
          );
          String outcome = challengeTelemetryService.recordVerifySuccess(
              challengeContext.contractVersion(),
              challengeContext.rolloutEnabled(),
              challengeContext.challengeCapableCohort(),
              correlationId,
              challengeIdHash
          );
          auditLogService.record(AuditLog.of(
              null,
              AuditAction.PASSWORD_RECOVERY_FORGOT,
              "PASSWORD_RECOVERY",
              null,
              challengeAuditDetail(outcome, challengeContext, null, challengeIdHash, false, null),
              clientIp,
              userAgent,
              correlationId
          ));
        } catch (BusinessException ex) {
          String challengeIdHash = challengeIdHash(
              challengeProvider == null
                  ? null
                  : challengeProvider.extractChallengeId(command.getChallengeToken())
          );
          String outcome = challengeTelemetryService.recordVerifyFailure(
              challengeContext.contractVersion(),
              challengeContext.rolloutEnabled(),
              challengeContext.challengeCapableCohort(),
              ex.getErrorCode(),
              correlationId,
              challengeIdHash
          );
          auditLogService.record(AuditLog.of(
              null,
              AuditAction.PASSWORD_RECOVERY_FORGOT,
              "PASSWORD_RECOVERY",
              null,
              challengeAuditDetail(
                  outcome,
                  challengeContext,
                  ex.getErrorCode(),
                  challengeIdHash,
                  isRetryable(ex.getErrorCode()),
                  null
              ),
              clientIp,
              userAgent,
              correlationId
          ));
          throw ex;
        }
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
    ChallengeRoutingDecision routingDecision = resolveChallengeRoutingDecision(normalizedEmail, request);

    rateLimitService.registerChallengeAttempt(clientIp, normalizedEmail);
    String contractVersion = routingDecision.proofOfWorkActive() ? "2" : "legacy-v1";
    PasswordRecoveryChallengeProvider.ChallengeEventContext challengeContext =
        challengeEventContext(contractVersion, routingDecision);
    try {
      PasswordRecoveryChallengeProvider provider = resolveChallengeBootstrapProvider(routingDecision);
      request.setAttribute(
          PasswordRecoveryChallengeProvider.ISSUE_CONTEXT_ROLLOUT_ENABLED_ATTRIBUTE,
          challengeContext.rolloutEnabled()
      );
      request.setAttribute(
          PasswordRecoveryChallengeProvider.ISSUE_CONTEXT_CHALLENGE_CAPABLE_COHORT_ATTRIBUTE,
          challengeContext.challengeCapableCohort()
      );
      PasswordForgotChallengeResult result = provider.issue(command.getEmail(), normalizedEmail, request);
      contractVersion = contractVersionLabel(result, provider);
      challengeContext = new PasswordRecoveryChallengeProvider.ChallengeEventContext(
          contractVersion,
          challengeContext.rolloutEnabled(),
          challengeContext.challengeCapableCohort()
      );
      String challengeIdHash = challengeIdHash(result.getChallengeId());
      rememberChallengeTelemetrySession(request, challengeContext, result, challengeIdHash);
      String outcome = challengeTelemetryService.recordBootstrapSuccess(
          challengeContext.contractVersion(),
          challengeContext.rolloutEnabled(),
          challengeContext.challengeCapableCohort(),
          correlationId,
          challengeIdHash
      );
      auditLogService.record(AuditLog.of(
          null,
          AuditAction.PASSWORD_RECOVERY_CHALLENGE_ISSUED,
          "PASSWORD_RECOVERY",
          null,
          challengeAuditDetail(outcome, challengeContext, null, challengeIdHash, false, null),
          clientIp,
          userAgent,
          correlationId
      ));
      return result;
    } catch (BusinessException ex) {
      String outcome = challengeTelemetryService.recordBootstrapFailure(
          challengeContext.contractVersion(),
          challengeContext.rolloutEnabled(),
          challengeContext.challengeCapableCohort(),
          ex.getErrorCode(),
          correlationId,
          null
      );
      auditLogService.record(AuditLog.of(
          null,
          AuditAction.PASSWORD_RECOVERY_CHALLENGE_ISSUED,
          "PASSWORD_RECOVERY",
          null,
          challengeAuditDetail(
              outcome,
              challengeContext,
              ex.getErrorCode(),
              null,
              isRetryable(ex.getErrorCode()),
              null
          ),
          clientIp,
          userAgent,
          correlationId
      ));
      throw ex;
    }
  }

  public void recordClientFailClosed(
      String reason,
      String surface,
      Long challengeIssuedAtEpochMs,
      HttpServletRequest request
  ) {
    String correlationId = resolveCorrelationId(request);
    ClientFailClosedResolution resolution =
        consumeStoredChallengeTelemetryContext(request, challengeIssuedAtEpochMs);
    if (resolution.dropReason() != null) {
      challengeTelemetryService.recordClientFailClosedDrop(
          reason,
          surface,
          resolution.dropReason(),
          correlationId
      );
      return;
    }
    StoredChallengeTelemetryContext storedContext = resolution.storedContext();
    if (storedContext == null) {
      challengeTelemetryService.recordClientFailClosedDrop(
          reason,
          surface,
          "missing-context",
          correlationId
      );
      return;
    }
    String clientIp = resolveClientIp(request);
    String userAgent = resolveUserAgent(request);
    String safeContractVersion = storedContext.contractVersion();
    PasswordRecoveryChallengeProvider.ChallengeEventContext challengeContext =
        new PasswordRecoveryChallengeProvider.ChallengeEventContext(
            safeContractVersion,
            storedContext.rolloutEnabled(),
            storedContext.challengeCapableCohort()
        );

    challengeTelemetryService.recordClientFailClosed(
        reason,
        surface,
        safeContractVersion,
        storedContext.rolloutEnabled(),
        storedContext.challengeCapableCohort(),
        correlationId,
        storedContext.challengeIdHash()
    );
    auditLogService.record(AuditLog.of(
        null,
        AuditAction.PASSWORD_RECOVERY_CHALLENGE_ISSUED,
        "PASSWORD_RECOVERY",
        null,
        challengeAuditDetail(
            "client_fail_closed",
            challengeContext,
            null,
            storedContext.challengeIdHash(),
            true,
            reason
        ),
        clientIp,
        userAgent,
        correlationId
    ));
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

  private PasswordRecoveryChallengeProvider resolveChallengeBootstrapProvider(ChallengeRoutingDecision routingDecision) {
    if (routingDecision.proofOfWorkActive()) {
      return challengeProviders.stream()
          .filter(PasswordRecoveryChallengeProvider::isProofOfWorkProvider)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("proof-of-work challenge provider is unavailable"));
    }

    return challengeProviders.stream()
        .filter(provider -> !provider.isProofOfWorkProvider())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("legacy challenge provider is unavailable"));
  }

  private PasswordRecoveryChallengeProvider resolveChallengeProvider(String challengeToken) {
    return challengeProviders.stream()
        .filter(provider -> provider.supportsToken(challengeToken))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("challenge provider is unavailable"));
  }

  private ChallengeRoutingDecision resolveChallengeRoutingDecision(String normalizedEmail, HttpServletRequest request) {
    PasswordRecoveryProperties.Challenge challenge = properties.getChallenge();
    ChallengeRoutingDecision deterministicOverride = resolveDeterministicChallengeRoutingDecision(challenge, request);
    if (deterministicOverride != null) {
      return deterministicOverride;
    }
    boolean rolloutEnabled = challenge.isV2Enabled();
    if (!rolloutEnabled) {
      return new ChallengeRoutingDecision(false, false, false);
    }

    int cohortPercentage = Math.max(0, Math.min(100, challenge.getCohortPercentage()));
    if (cohortPercentage <= 0) {
      return new ChallengeRoutingDecision(true, false, false);
    }
    if (cohortPercentage >= 100) {
      return new ChallengeRoutingDecision(true, true, true);
    }

    String sessionId = request.getSession(true).getId();
    String material = normalizedEmail + ":" + sessionId + ":" + challenge.getCohortSalt();
    String fingerprint = tokenService.fingerprint(material);
    long bucket = Long.parseUnsignedLong(fingerprint.substring(0, 8), 16) % 100L;
    boolean challengeCapableCohort = bucket < cohortPercentage;
    return new ChallengeRoutingDecision(true, challengeCapableCohort, challengeCapableCohort);
  }

  private ChallengeRoutingDecision resolveDeterministicChallengeRoutingDecision(
      PasswordRecoveryProperties.Challenge challenge,
      HttpServletRequest request
  ) {
    if (!challenge.isDeterministicOverrideEnabled() || !hasText(challenge.getDeterministicOverrideHeader())) {
      return null;
    }
    String requestedMode = request.getHeader(challenge.getDeterministicOverrideHeader());
    if (!hasText(requestedMode)) {
      return null;
    }

    return switch (requestedMode.trim().toLowerCase(Locale.ROOT)) {
      case DETERMINISTIC_OVERRIDE_V2 -> new ChallengeRoutingDecision(true, true, true);
      case DETERMINISTIC_OVERRIDE_LEGACY_V1 -> new ChallengeRoutingDecision(true, false, false);
      case DETERMINISTIC_OVERRIDE_DISABLED -> new ChallengeRoutingDecision(false, false, false);
      default -> null;
    };
  }

  private String contractVersionLabel(PasswordRecoveryChallengeProvider provider) {
    return provider.challengeContractVersionLabel();
  }

  private String contractVersionLabel(
      PasswordForgotChallengeResult result,
      PasswordRecoveryChallengeProvider provider
  ) {
    Integer contractVersion = result.getChallengeContractVersion();
    if (contractVersion != null) {
      return String.valueOf(contractVersion);
    }
    return contractVersionLabel(provider);
  }

  private String challengeAuditDetail(
      String outcome,
      PasswordRecoveryChallengeProvider.ChallengeEventContext challengeContext,
      ErrorCode errorCode,
      String challengeIdHash,
      boolean retryable,
      String clientFailClosedReason
  ) {
    StringBuilder detail = new StringBuilder("outcome=")
        .append(outcome)
        .append(", contractVersion=")
        .append(challengeContext.contractVersion())
        .append(", rolloutEnabled=")
        .append(challengeContext.rolloutEnabled())
        .append(", challengeCapableCohort=")
        .append(challengeContext.challengeCapableCohort())
        .append(", retryable=")
        .append(retryable);
    if (errorCode != null) {
      detail.append(", errorCode=").append(errorCode.code());
    }
    if (hasText(challengeIdHash)) {
      detail.append(", challengeIdHash=").append(challengeIdHash);
    }
    if (hasText(clientFailClosedReason)) {
      detail.append(", clientFailClosedReason=").append(clientFailClosedReason);
    }
    return detail.toString();
  }

  private PasswordRecoveryChallengeProvider.ChallengeEventContext challengeEventContext(
      String contractVersion,
      ChallengeRoutingDecision routingDecision
  ) {
    return new PasswordRecoveryChallengeProvider.ChallengeEventContext(
        contractVersion,
        routingDecision.rolloutEnabled(),
        routingDecision.challengeCapableCohort()
    );
  }

  private boolean isRetryable(ErrorCode errorCode) {
    return errorCode == ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_BOOTSTRAP_UNAVAILABLE
        || errorCode == ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_VERIFY_UNAVAILABLE;
  }

  private void rememberChallengeTelemetrySession(
      HttpServletRequest request,
      PasswordRecoveryChallengeProvider.ChallengeEventContext challengeContext,
      PasswordForgotChallengeResult result,
      String challengeIdHash
  ) {
    HttpSession session = request.getSession(true);
    if (!"2".equals(challengeContext.contractVersion())
        || !hasText(challengeIdHash)
        || result.getChallengeIssuedAtEpochMs() == null) {
      return;
    }

    ArrayList<PendingChallengeTelemetryContext> pendingContexts =
        readPendingChallengeTelemetryContexts(session);
    pendingContexts.add(new PendingChallengeTelemetryContext(
        challengeContext.contractVersion(),
        challengeContext.rolloutEnabled(),
        challengeContext.challengeCapableCohort(),
        result.getChallengeIssuedAtEpochMs(),
        challengeIdHash
    ));
    while (pendingContexts.size() > MAX_PENDING_CHALLENGE_TELEMETRY_CONTEXTS) {
      pendingContexts.remove(0);
    }
    writePendingChallengeTelemetryContexts(session, pendingContexts);
  }

  private ClientFailClosedResolution consumeStoredChallengeTelemetryContext(
      HttpServletRequest request,
      Long challengeIssuedAtEpochMs
  ) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return ClientFailClosedResolution.dropped("missing-session");
    }

    ArrayList<PendingChallengeTelemetryContext> pendingContexts =
        readPendingChallengeTelemetryContexts(session);
    if (pendingContexts.isEmpty()) {
      return ClientFailClosedResolution.dropped("missing-context");
    }

    if (challengeIssuedAtEpochMs == null) {
      if (pendingContexts.size() != 1) {
        return ClientFailClosedResolution.dropped("ambiguous-context");
      }

      PendingChallengeTelemetryContext onlyContext = pendingContexts.remove(0);
      writePendingChallengeTelemetryContexts(session, pendingContexts);
      return ClientFailClosedResolution.found(onlyContext.toStoredContext(false));
    }

    List<PendingChallengeTelemetryContext> matchingContexts = pendingContexts.stream()
        .filter(context -> Objects.equals(context.challengeIssuedAtEpochMs(), challengeIssuedAtEpochMs))
        .toList();
    if (matchingContexts.isEmpty()) {
      return ClientFailClosedResolution.dropped("timestamp-mismatch");
    }

    pendingContexts.removeIf(context -> Objects.equals(context.challengeIssuedAtEpochMs(), challengeIssuedAtEpochMs));
    writePendingChallengeTelemetryContexts(session, pendingContexts);

    PendingChallengeTelemetryContext referenceContext = matchingContexts.get(matchingContexts.size() - 1);
    return ClientFailClosedResolution.found(referenceContext.toStoredContext(matchingContexts.size() > 1));
  }

  private ArrayList<PendingChallengeTelemetryContext> readPendingChallengeTelemetryContexts(HttpSession session) {
    Object storedContexts = session.getAttribute(SESSION_PENDING_CHALLENGE_TELEMETRY_CONTEXTS_ATTRIBUTE);
    ArrayList<PendingChallengeTelemetryContext> resolvedContexts = new ArrayList<>();
    if (!(storedContexts instanceof List<?> rawContexts)) {
      return resolvedContexts;
    }

    for (Object candidate : rawContexts) {
      if (candidate instanceof PendingChallengeTelemetryContext context) {
        resolvedContexts.add(context);
      }
    }
    return resolvedContexts;
  }

  private void writePendingChallengeTelemetryContexts(
      HttpSession session,
      List<PendingChallengeTelemetryContext> pendingContexts
  ) {
    if (pendingContexts.isEmpty()) {
      session.removeAttribute(SESSION_PENDING_CHALLENGE_TELEMETRY_CONTEXTS_ATTRIBUTE);
      return;
    }
    session.setAttribute(
        SESSION_PENDING_CHALLENGE_TELEMETRY_CONTEXTS_ATTRIBUTE,
        new ArrayList<>(pendingContexts)
    );
  }

  private String challengeIdHash(String challengeId) {
    if (!hasText(challengeId)) {
      return null;
    }

    try {
      Mac mac = Mac.getInstance(CHALLENGE_ID_HASH_ALGORITHM);
      mac.init(new SecretKeySpec(
          properties.getChallenge().getObservabilitySecret().getBytes(StandardCharsets.UTF_8),
          CHALLENGE_ID_HASH_ALGORITHM
      ));
      String hexDigest = HexFormat.of().formatHex(mac.doFinal(challengeId.getBytes(StandardCharsets.UTF_8)));
      return hexDigest.substring(0, Math.min(CHALLENGE_ID_HASH_LENGTH, hexDigest.length()));
    } catch (Exception ex) {
      throw new IllegalStateException("failed to derive password recovery challenge observability hash", ex);
    }
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

  private record ChallengeRoutingDecision(
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      boolean proofOfWorkActive
  ) {
  }

  private record StoredChallengeTelemetryContext(
      String contractVersion,
      boolean rolloutEnabled,
      boolean challengeCapableCohort,
      String challengeIdHash
  ) {
  }

  private record ClientFailClosedResolution(
      StoredChallengeTelemetryContext storedContext,
      String dropReason
  ) {
    private static ClientFailClosedResolution found(StoredChallengeTelemetryContext storedContext) {
      return new ClientFailClosedResolution(storedContext, null);
    }

    private static ClientFailClosedResolution dropped(String dropReason) {
      return new ClientFailClosedResolution(null, dropReason);
    }
  }

  private static final class PendingChallengeTelemetryContext implements Serializable {
    private final String contractVersion;
    private final boolean rolloutEnabled;
    private final boolean challengeCapableCohort;
    private final Long challengeIssuedAtEpochMs;
    private final String challengeIdHash;

    private PendingChallengeTelemetryContext(
        String contractVersion,
        boolean rolloutEnabled,
        boolean challengeCapableCohort,
        Long challengeIssuedAtEpochMs,
        String challengeIdHash
    ) {
      this.contractVersion = contractVersion;
      this.rolloutEnabled = rolloutEnabled;
      this.challengeCapableCohort = challengeCapableCohort;
      this.challengeIssuedAtEpochMs = challengeIssuedAtEpochMs;
      this.challengeIdHash = challengeIdHash;
    }

    private Long challengeIssuedAtEpochMs() {
      return challengeIssuedAtEpochMs;
    }

    private StoredChallengeTelemetryContext toStoredContext(boolean hashAmbiguous) {
      return new StoredChallengeTelemetryContext(
          contractVersion,
          rolloutEnabled,
          challengeCapableCohort,
          hashAmbiguous ? null : challengeIdHash
      );
    }
  }
}
