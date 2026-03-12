package com.fix.channel.vo;

import com.fix.channel.entity.OrderSessionAuthorizationReason;
import com.fix.channel.entity.OrderSessionStatus;

public class OrderSessionAuthorizationDecision {

  private final OrderSessionStatus initialStatus;
  private final boolean challengeRequired;
  private final OrderSessionAuthorizationReason authorizationReason;

  private OrderSessionAuthorizationDecision(
      OrderSessionStatus initialStatus,
      boolean challengeRequired,
      OrderSessionAuthorizationReason authorizationReason
  ) {
    this.initialStatus = initialStatus;
    this.challengeRequired = challengeRequired;
    this.authorizationReason = authorizationReason;
  }

  public static OrderSessionAuthorizationDecision challengeRequired() {
    return new OrderSessionAuthorizationDecision(
        OrderSessionStatus.PENDING_NEW,
        true,
        OrderSessionAuthorizationReason.STEP_UP_REQUIRED
    );
  }

  public static OrderSessionAuthorizationDecision autoAuthorized() {
    return new OrderSessionAuthorizationDecision(
        OrderSessionStatus.AUTHED,
        false,
        OrderSessionAuthorizationReason.LOGIN_MFA_FRESH
    );
  }

  public OrderSessionStatus getInitialStatus() {
    return initialStatus;
  }

  public boolean isChallengeRequired() {
    return challengeRequired;
  }

  public OrderSessionAuthorizationReason getAuthorizationReason() {
    return authorizationReason;
  }
}
