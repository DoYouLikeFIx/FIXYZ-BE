package com.fix.channel.vo;

import java.time.Instant;

public class OrderSessionCreateCommand {

  private final Long memberId;
  private final String clOrdId;
  private final String orderRef;
  private final Instant lastMfaVerifiedAt;
  private final Instant loginAuthenticatedAt;
  private final boolean challengeBypassEligible;
  private final String loginIpAddress;
  private final String loginUserAgent;
  private final String clientIpAddress;
  private final String clientUserAgent;

  private OrderSessionCreateCommand(
      Long memberId,
      String clOrdId,
      String orderRef,
      Instant lastMfaVerifiedAt,
      Instant loginAuthenticatedAt,
      boolean challengeBypassEligible,
      String loginIpAddress,
      String loginUserAgent,
      String clientIpAddress,
      String clientUserAgent
  ) {
    this.memberId = memberId;
    this.clOrdId = clOrdId;
    this.orderRef = orderRef;
    this.lastMfaVerifiedAt = lastMfaVerifiedAt;
    this.loginAuthenticatedAt = loginAuthenticatedAt;
    this.challengeBypassEligible = challengeBypassEligible;
    this.loginIpAddress = loginIpAddress;
    this.loginUserAgent = loginUserAgent;
    this.clientIpAddress = clientIpAddress;
    this.clientUserAgent = clientUserAgent;
  }

  public static OrderSessionCreateCommand of(Long memberId, String clOrdId, String orderRef) {
    return new OrderSessionCreateCommand(memberId, clOrdId, orderRef, null, null, false, null, null, null, null);
  }

  public static OrderSessionCreateCommand of(
      Long memberId,
      String clOrdId,
      String orderRef,
      Instant lastMfaVerifiedAt,
      boolean challengeBypassEligible
  ) {
    return of(
        memberId,
        clOrdId,
        orderRef,
        lastMfaVerifiedAt,
        null,
        challengeBypassEligible,
        null,
        null,
        null,
        null
    );
  }

  public static OrderSessionCreateCommand of(
      Long memberId,
      String clOrdId,
      String orderRef,
      Instant lastMfaVerifiedAt,
      boolean challengeBypassEligible,
      String loginIpAddress,
      String loginUserAgent,
      String clientIpAddress,
      String clientUserAgent
  ) {
    return of(
        memberId,
        clOrdId,
        orderRef,
        lastMfaVerifiedAt,
        null,
        challengeBypassEligible,
        loginIpAddress,
        loginUserAgent,
        clientIpAddress,
        clientUserAgent
    );
  }

  public static OrderSessionCreateCommand of(
      Long memberId,
      String clOrdId,
      String orderRef,
      Instant lastMfaVerifiedAt,
      Instant loginAuthenticatedAt,
      boolean challengeBypassEligible,
      String loginIpAddress,
      String loginUserAgent,
      String clientIpAddress,
      String clientUserAgent
  ) {
    return new OrderSessionCreateCommand(
        memberId,
        clOrdId,
        orderRef,
        lastMfaVerifiedAt,
        loginAuthenticatedAt,
        challengeBypassEligible,
        loginIpAddress,
        loginUserAgent,
        clientIpAddress,
        clientUserAgent
    );
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getOrderRef() {
    return orderRef;
  }

  public Instant getLastMfaVerifiedAt() {
    return lastMfaVerifiedAt;
  }

  public Instant getLoginAuthenticatedAt() {
    return loginAuthenticatedAt;
  }

  public boolean isChallengeBypassEligible() {
    return challengeBypassEligible;
  }

  public String getLoginIpAddress() {
    return loginIpAddress;
  }

  public String getLoginUserAgent() {
    return loginUserAgent;
  }

  public String getClientIpAddress() {
    return clientIpAddress;
  }

  public String getClientUserAgent() {
    return clientUserAgent;
  }
}
