package com.fix.channel.testsupport;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.vo.OrderSessionCreateCommand;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.boot.test.context.TestComponent;

@TestComponent
public class OrderSessionTestFixture {

  private final OrderSessionRepository orderSessionRepository;

  public OrderSessionTestFixture(OrderSessionRepository orderSessionRepository) {
    this.orderSessionRepository = orderSessionRepository;
  }

  public void reset() {
    orderSessionRepository.deleteAll();
  }

  public OrderSession createInitiatedSession(
      Long memberId,
      Long accountId,
      String orderSessionId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      boolean challengeRequired,
      String authorizationReason,
      Instant expiresAt
  ) {
    return orderSessionRepository.saveAndFlush(OrderSession.initiated(
        memberId,
        accountId,
        orderSessionId,
        replayFingerprint(accountId, symbol, side, orderType, qty, price),
        symbol,
        side,
        orderType,
        qty,
        price,
        challengeRequired,
        authorizationReason,
        expiresAt
    ));
  }

  public String createInitiatedSessionId(
      Long memberId,
      Long accountId,
      String orderSessionId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      boolean challengeRequired,
      String authorizationReason,
      Instant expiresAt
  ) {
    return createInitiatedSession(
        memberId,
        accountId,
        orderSessionId,
        symbol,
        side,
        orderType,
        qty,
        price,
        challengeRequired,
        authorizationReason,
        expiresAt
    ).getOrderSessionId();
  }

  private String replayFingerprint(
      Long accountId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price
  ) {
    return OrderSessionCreateCommand.of(
        301L,
        accountId,
        "fixture-only",
        symbol,
        side,
        orderType,
        qty,
        price
    ).replayFingerprint();
  }
}
