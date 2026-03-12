package com.fix.channel.service;

import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.vo.OrderSessionAuthorizationDecision;
import com.fix.channel.vo.OrderSessionCreateCommand;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderSessionAuthorizationDecisionService {

  private static final Duration FRESH_LOGIN_MFA_WINDOW = Duration.ofMinutes(5);
  private static final Duration LOW_RISK_ORDER_VELOCITY_WINDOW = Duration.ofMinutes(10);
  private static final Duration RECENT_SECURITY_EVENT_WINDOW = Duration.ofMinutes(30);

  private final OrderSessionRepository orderSessionRepository;
  private final SecurityEventRepository securityEventRepository;

  public OrderSessionAuthorizationDecision evaluate(OrderSessionCreateCommand command) {
    Instant now = Instant.now();
    if (command.isChallengeBypassEligible()
        && hasFreshLoginMfaProof(command.getLoginAuthenticatedAt(), command.getLastMfaVerifiedAt(), now)
        && hasTrustedSessionContinuity(command)
        && !hasRecentSecurityEvent(command.getMemberId(), now)
        && !hasRecentOrderVelocity(command.getMemberId(), now)) {
      return OrderSessionAuthorizationDecision.autoAuthorized();
    }
    return OrderSessionAuthorizationDecision.challengeRequired();
  }

  private boolean hasFreshLoginMfaProof(
      Instant loginAuthenticatedAt,
      Instant lastMfaVerifiedAt,
      Instant now
  ) {
    if (loginAuthenticatedAt == null || lastMfaVerifiedAt == null) {
      return false;
    }
    return !lastMfaVerifiedAt.isBefore(loginAuthenticatedAt)
        && !lastMfaVerifiedAt.isBefore(now.minus(FRESH_LOGIN_MFA_WINDOW));
  }

  private boolean hasTrustedSessionContinuity(OrderSessionCreateCommand command) {
    return hasText(command.getLoginIpAddress())
        && hasText(command.getClientIpAddress())
        && Objects.equals(command.getLoginIpAddress(), command.getClientIpAddress())
        && Objects.equals(normalize(command.getLoginUserAgent()), normalize(command.getClientUserAgent()));
  }

  private boolean hasRecentSecurityEvent(Long memberId, Instant now) {
    return securityEventRepository.countByMemberIdAndCreatedAtAfter(
        memberId,
        now.minus(RECENT_SECURITY_EVENT_WINDOW)
    ) > 0;
  }

  private boolean hasRecentOrderVelocity(Long memberId, Instant now) {
    return orderSessionRepository.countByMemberIdAndCreatedAtAfter(
        memberId,
        now.minus(LOW_RISK_ORDER_VELOCITY_WINDOW)
    ) > 0;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String normalize(String value) {
    return value == null ? null : value.trim();
  }
}
