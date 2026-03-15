package com.fix.channel.service;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.dto.response.OrderSessionResponse;
import com.fix.channel.vo.AccountPositionQueryCommand;
import com.fix.channel.vo.AccountSummaryQueryCommand;
import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.channel.vo.OrderSessionOtpVerifyCommand;
import com.fix.channel.vo.OrderSessionQueryCommand;
import com.fix.channel.vo.OrderSessionResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderSessionService {

  private static final String AUTHORIZATION_REASON_ELEVATED_ORDER_RISK = "ELEVATED_ORDER_RISK";
  private static final String AUTHORIZATION_REASON_TRUSTED_AUTH_SESSION = "TRUSTED_AUTH_SESSION";
  private static final Set<String> RECENT_RISK_SECURITY_EVENTS = Set.of(
      "ACCOUNT_LOCKED",
      "MFA_RECOVERY_PROOF_ISSUED",
      "MFA_REBIND_INITIATED",
      "MFA_REBIND_COMPLETED",
      "MFA_REBIND_FAILED"
  );

  private final OrderSessionRepository orderSessionRepository;
  private final MemberRepository memberRepository;
  private final SecurityEventRepository securityEventRepository;
  private final SessionOwnershipValidator sessionOwnershipValidator;
  private final OrderSessionPersistenceService orderSessionPersistenceService;
  private final OrderSessionRateLimitService orderSessionRateLimitService;
  private final OrderSessionOtpChallengeService orderSessionOtpChallengeService;
  private final OrderSessionTtlStore orderSessionTtlStore;
  private final AccountPositionService accountPositionService;
  private final TotpService totpService;
  private final Clock clock;

  @Value("${auth.guardrails.order-authorization.recent-login-mfa-window:60m}")
  private Duration recentLoginMfaWindow;

  @Value("${auth.guardrails.order-authorization.auto-authorize.max-notional:500000}")
  private BigDecimal autoAuthorizeMaxNotional;

  @Value("${auth.guardrails.order-authorization.recent-security-event-window:24h}")
  private Duration recentSecurityEventWindow;

  public OrderSessionResult createOrderSession(OrderSessionCreateCommand command) {
    sessionOwnershipValidator.validateLinkedAccount(command.getMemberId(), command.getAccountId());

    OrderSession existingSession = orderSessionRepository.findByClOrdId(command.getClOrdId()).orElse(null);
    if (existingSession != null) {
      return replayExistingSession(existingSession, command);
    }

    orderSessionRateLimitService.enforceCreateRateLimit(command.getMemberId());
    validatePreTradeEligibility(command);

    OrderSession savedSession;
    Instant expiresAt = Instant.now(clock).plus(orderSessionTtlStore.ttl());
    AuthorizationDecision authorizationDecision = resolveAuthorizationDecision(command);
    try {
      savedSession = orderSessionPersistenceService.createSession(
          command,
          authorizationDecision.challengeRequired(),
          authorizationDecision.authorizationReason(),
          expiresAt
      );
    } catch (DataIntegrityViolationException ex) {
      return resolveConcurrentReplay(command, ex);
    }

    try {
      orderSessionTtlStore.activate(savedSession.getOrderSessionId(), savedSession.getExpiresAt());
    } catch (RuntimeException ex) {
      cleanupFailedActivation(savedSession.getOrderSessionId(), command.getMemberId(), ex);
      throw ex;
    }

    return buildResult(savedSession, requireRemainingSeconds(savedSession), true);
  }

  public OrderSessionCreateResponse createOrderSessionResponse(OrderSessionCreateCommand command) {
    OrderSessionResult result = createOrderSession(command);
    return new OrderSessionCreateResponse(OrderSessionResponse.from(result), result.isCreated());
  }

  public OrderSessionResult getOrderSession(OrderSessionQueryCommand command) {
    OrderSession session = requireOwnedSession(command.getMemberId(), command.getOrderSessionId());
    return buildResult(session, requireRemainingSeconds(session), false);
  }

  public OrderSessionResponse getOrderSessionResponse(OrderSessionQueryCommand command) {
    return OrderSessionResponse.from(getOrderSession(command));
  }

  public OrderSessionResult extendOrderSession(OrderSessionQueryCommand command) {
    OrderSession session = requireOwnedSession(command.getMemberId(), command.getOrderSessionId());
    requireRemainingSeconds(session);
    if (!isExtendableStatus(session.getStatus())) {
      throw new BusinessException(
          ErrorCode.ORDER_SESSION_NOT_AUTHORIZED,
          "order session cannot be extended in current state"
      );
    }

    // Extension preserves an already-active order session. It does not replay
    // create-time auto-authorization gates such as recent MFA freshness.
    Instant previousExpiresAt = resolveExpiresAt(session);
    Instant nextExpiresAt = resolveNextExpiry(previousExpiresAt);
    OrderSession extendedSession = extendSession(session, nextExpiresAt);
    try {
      orderSessionTtlStore.refresh(extendedSession.getOrderSessionId(), nextExpiresAt);
    } catch (RuntimeException ex) {
      orderSessionPersistenceService.restoreExpiry(extendedSession, previousExpiresAt);
      throw ex;
    }
    return buildResult(extendedSession, requireRemainingSeconds(extendedSession), false);
  }

  public OrderSessionResponse extendOrderSessionResponse(OrderSessionQueryCommand command) {
    return OrderSessionResponse.from(extendOrderSession(command));
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public OrderSessionResult verifyOtp(OrderSessionOtpVerifyCommand command) {
    OrderSession session = requireOwnedSession(command.getMemberId(), command.getOrderSessionId());
    Long remainingSeconds = requireRemainingSeconds(session);
    if (session.getStatus() == OrderSessionStatus.AUTHED) {
      throw new BusinessException(
          ErrorCode.ORDER_SESSION_NOT_AUTHORIZED,
          "order session is already authorized"
      );
    }
    if (session.getStatus() != OrderSessionStatus.PENDING_NEW) {
      throw new BusinessException(
          ErrorCode.ORDER_SESSION_NOT_AUTHORIZED,
          "order session is not awaiting otp verification"
      );
    }

    Member member = memberRepository.findByIdForUpdate(command.getMemberId())
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required"));
    if (!member.isTotpEnabled() || !totpService.hasActiveSecret(member)) {
      throw new BusinessException(
          ErrorCode.ORDER_SESSION_TOTP_REQUIRED,
          "totp enrollment required",
          new ErrorMetadata(null, null, Map.of("enrollUrl", "/settings/totp/enroll"))
      );
    }

    orderSessionOtpChallengeService.enforceDebounce(session.getOrderSessionId());
    if (orderSessionOtpChallengeService.remainingAttempts(session.getOrderSessionId()) < 1) {
      markFailed(session, "OTP_EXCEEDED");
      throw new BusinessException(ErrorCode.CHANNEL_OTP_ATTEMPTS_EXCEEDED, "otp attempts exceeded");
    }

    TotpService.TotpVerification verification = totpService.verifyCurrentCode(member, command.getOtpCode());
    if (!verification.matched()) {
      int remainingAttempts = orderSessionOtpChallengeService.consumeFailure(session.getOrderSessionId());
      if (remainingAttempts < 1) {
        markFailed(session, "OTP_EXCEEDED");
        throw new BusinessException(ErrorCode.CHANNEL_OTP_ATTEMPTS_EXCEEDED, "otp attempts exceeded");
      }
      throw new BusinessException(
          ErrorCode.CHANNEL_OTP_MISMATCH,
          "otp code mismatch",
          new ErrorMetadata(null, null, Map.of("remainingAttempts", remainingAttempts))
      );
    }

    if (orderSessionOtpChallengeService.isSuccessfulReplay(
        session.getOrderSessionId(),
        verification.windowIndex(),
        verification.normalizedOtp()
    )) {
      return buildResult(reloadLatestSession(session), remainingSeconds, false);
    }

    OrderSession authorizedSession = authorize(session);
    orderSessionOtpChallengeService.recordSuccess(
        authorizedSession.getOrderSessionId(),
        verification.windowIndex(),
        verification.normalizedOtp()
    );
    return buildResult(authorizedSession, remainingSeconds, false);
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public OrderSessionResponse verifyOtpResponse(OrderSessionOtpVerifyCommand command) {
    return OrderSessionResponse.from(verifyOtp(command));
  }

  public OrderSession authorize(OrderSession session) {
    return orderSessionPersistenceService.markAuthorized(session);
  }

  public OrderSession extendSession(OrderSession session, Instant expiresAt) {
    return orderSessionPersistenceService.extendSession(session, expiresAt);
  }

  public OrderSession beginExecution(OrderSession session) {
    return orderSessionPersistenceService.markExecuting(session);
  }

  public OrderSession completeExecution(
      OrderSession session,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      Instant executedAt
  ) {
    return orderSessionPersistenceService.markCompleted(
        session,
        executionResult,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        executedAt
    );
  }

  public OrderSession markFailed(OrderSession session, String failureReason) {
    return orderSessionPersistenceService.markFailed(session, failureReason);
  }

  public void expireSession(String orderSessionId) {
    orderSessionPersistenceService.expireSession(orderSessionId);
  }

  public java.util.List<String> expireOverdueSessionBatch(Instant referenceTime, int batchSize) {
    return orderSessionPersistenceService.expireOverdueSessionBatch(referenceTime, batchSize);
  }

  private OrderSession resolveSession(OrderSessionQueryCommand command) {
    return orderSessionRepository.findByOrderSessionId(command.getOrderSessionId())
        .orElseThrow(this::orderSessionNotFound);
  }

  OrderSession requireOwnedSession(Long memberId, String orderSessionId) {
    OrderSession session = orderSessionRepository.findByOrderSessionId(orderSessionId)
        .orElseThrow(this::orderSessionNotFound);
    sessionOwnershipValidator.validateOwner(session, memberId);
    return session;
  }

  private Instant resolveExpiresAt(OrderSession session) {
    Instant expiresAt = session.getExpiresAt();
    if (expiresAt == null) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "order session expiration timestamp missing");
    }
    return expiresAt;
  }

  private Instant resolveNextExpiry(Instant previousExpiresAt) {
    Instant candidateExpiresAt = Instant.now(clock).plus(orderSessionTtlStore.ttl());
    return candidateExpiresAt.isAfter(previousExpiresAt) ? candidateExpiresAt : previousExpiresAt;
  }

  private OrderSessionResult buildResult(OrderSession session, Long remainingSeconds, boolean created) {
    return OrderSessionResult.of(
        session.getOrderSessionId(),
        session.getClOrdId(),
        session.getStatus().name(),
        session.isChallengeRequired(),
        session.getAuthorizationReason(),
        session.getAccountId(),
        session.getSymbol(),
        session.getSide(),
        session.getOrderType(),
        session.getQty(),
        session.getPrice(),
        null,
        null,
        null,
        null,
        resolveExpiresAt(session),
        remainingSeconds,
        session.getExecutionResult(),
        session.getExecutedQty(),
        session.getLeavesQty(),
        session.getExecutedPrice(),
        session.getExternalOrderId(),
        session.getFailureReason(),
        session.getExecutedAt(),
        session.getCanceledAt(),
        session.getCreatedAt(),
        session.getUpdatedAt(),
        created
    );
  }

  private AuthorizationDecision resolveAuthorizationDecision(OrderSessionCreateCommand command) {
    if (isLowRiskOrder(command)
        && hasFreshLoginMfaForCreateAutoAuth(command.getLastMfaVerifiedAt())
        && hasLoginContextContinuity(command)
        && !hasRecentRiskSecurityEvents(command.getMemberId())) {
      return new AuthorizationDecision(false, AUTHORIZATION_REASON_TRUSTED_AUTH_SESSION);
    }
    return new AuthorizationDecision(true, AUTHORIZATION_REASON_ELEVATED_ORDER_RISK);
  }

  private boolean isExtendableStatus(OrderSessionStatus status) {
    return status == OrderSessionStatus.PENDING_NEW || status == OrderSessionStatus.AUTHED;
  }

  private boolean hasFreshLoginMfaForCreateAutoAuth(Instant lastMfaVerifiedAt) {
    if (lastMfaVerifiedAt == null) {
      return false;
    }
    Instant now = Instant.now(clock);
    if (lastMfaVerifiedAt.isAfter(now)) {
      return false;
    }
    return Duration.between(lastMfaVerifiedAt, now).compareTo(recentLoginMfaWindow) <= 0;
  }

  private boolean isLowRiskOrder(OrderSessionCreateCommand command) {
    if (!"LIMIT".equals(command.getOrderType()) || command.getPrice() == null) {
      return false;
    }
    BigDecimal orderNotional = command.getQty().multiply(command.getPrice());
    return orderNotional.compareTo(autoAuthorizeMaxNotional) <= 0;
  }

  private boolean hasLoginContextContinuity(OrderSessionCreateCommand command) {
    return sameSignal(command.getLoginClientIp(), command.getRequestClientIp())
        && sameSignal(command.getLoginUserAgent(), command.getRequestUserAgent());
  }

  private boolean sameSignal(String loginValue, String requestValue) {
    return normalizeSignal(loginValue).equals(normalizeSignal(requestValue));
  }

  private String normalizeSignal(String value) {
    return value == null ? "" : value.trim();
  }

  private boolean hasRecentRiskSecurityEvents(Long memberId) {
    Instant threshold = Instant.now(clock).minus(recentSecurityEventWindow);
    return securityEventRepository.findByMemberId(
            memberId,
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"))
        ).stream()
        .anyMatch(event ->
            RECENT_RISK_SECURITY_EVENTS.contains(event.getEventType())
                && event.getCreatedAt() != null
                && !event.getCreatedAt().isBefore(threshold)
        );
  }

  private OrderSession reloadLatestSession(OrderSession fallbackSession) {
    return orderSessionRepository.findByOrderSessionId(fallbackSession.getOrderSessionId())
        .orElse(fallbackSession);
  }

  private void validatePreTradeEligibility(OrderSessionCreateCommand command) {
    if ("BUY".equals(command.getSide())) {
      validateBuyCapacity(command);
      return;
    }
    if ("SELL".equals(command.getSide())) {
      validateSellCapacity(command);
    }
  }

  private void validateBuyCapacity(OrderSessionCreateCommand command) {
    if (command.getPrice() == null) {
      return;
    }
    BigDecimal requiredNotional = command.getQty().multiply(command.getPrice());
    BigDecimal availableBalance = accountPositionService.getAccountSummary(
            AccountSummaryQueryCommand.of(command.getAccountId(), command.getMemberId())
        ).getBalance();
    if (availableBalance.compareTo(requiredNotional) < 0) {
      throw new BusinessException(ErrorCode.ORD_INSUFFICIENT_CASH, "available cash is insufficient");
    }
  }

  private void validateSellCapacity(OrderSessionCreateCommand command) {
    BigDecimal availableQuantity = accountPositionService.getAccountPosition(
            AccountPositionQueryCommand.of(command.getAccountId(), command.getMemberId(), command.getSymbol())
        ).getAvailableQuantity();
    if (availableQuantity.compareTo(command.getQty()) < 0) {
      throw new BusinessException(ErrorCode.ORD_INSUFFICIENT_POSITION, "insufficient position quantity");
    }
  }

  private OrderSessionResult resolveConcurrentReplay(OrderSessionCreateCommand command, DataIntegrityViolationException ex) {
    String concurrentSessionId = null;
    RuntimeException failure = ex;
    try {
      OrderSession concurrentSession = orderSessionRepository.findByClOrdId(command.getClOrdId())
          .orElseThrow(() -> ex);
      concurrentSessionId = concurrentSession.getOrderSessionId();
      sessionOwnershipValidator.validateOwner(concurrentSession, command.getMemberId());
      Long remainingSeconds = requireRemainingSeconds(concurrentSession);
      validateReplayPayload(concurrentSession, command);
      return buildResult(concurrentSession, remainingSeconds, false);
    } catch (RuntimeException runtimeEx) {
      failure = runtimeEx;
      throw runtimeEx;
    } finally {
      safelyRefundCreateRateLimit(
          command.getMemberId(),
          concurrentSessionId,
          "duplicate create recovery",
          failure
      );
    }
  }

  private OrderSessionResult replayExistingSession(OrderSession session, OrderSessionCreateCommand command) {
    sessionOwnershipValidator.validateOwner(session, command.getMemberId());
    Long remainingSeconds = requireRemainingSeconds(session);
    validateReplayPayload(session, command);
    return buildResult(session, remainingSeconds, false);
  }

  private void validateReplayPayload(OrderSession session, OrderSessionCreateCommand command) {
    if (!session.matchesReplayFingerprint(command.replayFingerprint())) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "clOrdId replay payload mismatch");
    }
  }

  private Long expireAndReturnMissing(OrderSession session) {
    safelyClearTtl(session.getOrderSessionId(), null, "missing session reconciliation");
    expireSession(session.getOrderSessionId());
    throw orderSessionNotFound();
  }

  private Long requireRemainingSeconds(OrderSession session) {
    Instant expiresAt = resolveExpiresAt(session);
    if (!expiresAt.isAfter(Instant.now(clock))) {
      return expireAndReturnMissing(session);
    }
    if (!orderSessionTtlStore.isActive(session.getOrderSessionId())) {
      return expireAndReturnMissing(session);
    }
    return remainingSecondsUntil(expiresAt);
  }

  private Long remainingSecondsUntil(Instant expiresAt) {
    long remainingMillis = Duration.between(Instant.now(clock), expiresAt).toMillis();
    long roundedSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
    return Math.min(orderSessionTtlStore.ttl().toSeconds(), roundedSeconds);
  }

  private void cleanupFailedActivation(String orderSessionId, Long memberId, RuntimeException original) {
    safelyClearTtl(orderSessionId, original, "activation rollback");
    try {
      orderSessionPersistenceService.deleteCreatedSession(orderSessionId);
    } catch (RuntimeException cleanupFailure) {
      log.error("Failed to delete partially-created order session during activation rollback: orderSessionId={}",
          orderSessionId, cleanupFailure);
      original.addSuppressed(cleanupFailure);
    }
    safelyRefundCreateRateLimit(memberId, orderSessionId, "activation rollback", original);
  }

  private void safelyClearTtl(String orderSessionId, RuntimeException primaryFailure, String context) {
    try {
      orderSessionTtlStore.clear(orderSessionId);
    } catch (RuntimeException cleanupFailure) {
      log.warn("Failed to clear order session TTL during {}: orderSessionId={}", context, orderSessionId, cleanupFailure);
      if (primaryFailure != null) {
        primaryFailure.addSuppressed(cleanupFailure);
      }
    }
  }

  private void safelyRefundCreateRateLimit(
      Long memberId,
      String orderSessionId,
      String context,
      RuntimeException primaryFailure
  ) {
    try {
      orderSessionRateLimitService.refundCreateRateLimit(memberId);
    } catch (RuntimeException refundFailure) {
      log.warn("Failed to refund order session rate limit during {}: orderSessionId={}, memberId={}",
          context, orderSessionId, memberId, refundFailure);
      if (primaryFailure != null) {
        primaryFailure.addSuppressed(refundFailure);
      }
    }
  }

  private BusinessException orderSessionNotFound() {
    return new BusinessException(ErrorCode.ORDER_SESSION_NOT_FOUND, "Order session not found.");
  }

  private record AuthorizationDecision(boolean challengeRequired, String authorizationReason) {
  }

  public record OrderSessionCreateResponse(OrderSessionResponse response, boolean created) {
  }
}
