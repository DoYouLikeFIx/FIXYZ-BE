package com.fix.channel.testsupport;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.service.OrderSessionTtlStore;
import com.fix.channel.vo.OrderSessionCreateCommand;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestComponent;

@TestComponent
public class OrderSessionTestFixture {

  private final OrderSessionRepository orderSessionRepository;
  private final ObjectProvider<OrderSessionTtlStore> orderSessionTtlStoreProvider;

  public OrderSessionTestFixture(
      OrderSessionRepository orderSessionRepository,
      ObjectProvider<OrderSessionTtlStore> orderSessionTtlStoreProvider
  ) {
    this.orderSessionRepository = orderSessionRepository;
    this.orderSessionTtlStoreProvider = orderSessionTtlStoreProvider;
  }

  public void reset() {
    orderSessionRepository.deleteAll();
  }

  public OrderSession createInitiatedSession(
      Long memberId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      boolean challengeRequired,
      String authorizationReason,
      Instant expiresAt
  ) {
    OrderSession session = orderSessionRepository.saveAndFlush(OrderSession.initiated(
        memberId,
        accountId,
        clOrdId,
        replayFingerprint(memberId, accountId, symbol, side, orderType, qty, price),
        symbol,
        side,
        orderType,
        qty,
        price,
        challengeRequired,
        authorizationReason,
        expiresAt
    ));
    activateTtl(session);
    return session;
  }

  public String createInitiatedSessionId(
      Long memberId,
      Long accountId,
      String clOrdId,
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
        clOrdId,
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

  public String createExecutingSessionId(
      Long memberId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      boolean challengeRequired,
      String authorizationReason,
      Instant expiresAt
  ) {
    OrderSession session = createInitiatedSession(
        memberId,
        accountId,
        clOrdId,
        symbol,
        side,
        orderType,
        qty,
        price,
        challengeRequired,
        authorizationReason,
        expiresAt
    );
    session.startExecuting();
    return orderSessionRepository.saveAndFlush(session).getOrderSessionId();
  }

  public String statusOf(String orderSessionId) {
    return orderSessionRepository.findByOrderSessionId(orderSessionId)
        .map(session -> session.getStatus().name())
        .orElse(null);
  }

  public String failureReasonOf(String orderSessionId) {
    return orderSessionRepository.findByOrderSessionId(orderSessionId)
        .map(OrderSession::getFailureReason)
        .orElse(null);
  }

  public String executionResultOf(String orderSessionId) {
    return orderSessionRepository.findByOrderSessionId(orderSessionId)
        .map(OrderSession::getExecutionResult)
        .orElse(null);
  }

  public BigDecimal executedQtyOf(String orderSessionId) {
    return orderSessionRepository.findByOrderSessionId(orderSessionId)
        .map(OrderSession::getExecutedQty)
        .orElse(null);
  }

  public BigDecimal leavesQtyOf(String orderSessionId) {
    return orderSessionRepository.findByOrderSessionId(orderSessionId)
        .map(OrderSession::getLeavesQty)
        .orElse(null);
  }

  public BigDecimal executedPriceOf(String orderSessionId) {
    return orderSessionRepository.findByOrderSessionId(orderSessionId)
        .map(OrderSession::getExecutedPrice)
        .orElse(null);
  }

  public String externalOrderIdOf(String orderSessionId) {
    return orderSessionRepository.findByOrderSessionId(orderSessionId)
        .map(OrderSession::getExternalOrderId)
        .orElse(null);
  }

  private void activateTtl(OrderSession session) {
    OrderSessionTtlStore orderSessionTtlStore = orderSessionTtlStoreProvider.getIfAvailable();
    if (orderSessionTtlStore == null) {
      return;
    }
    orderSessionTtlStore.activate(session.getOrderSessionId(), session.getExpiresAt());
  }

  private String replayFingerprint(
      Long memberId,
      Long accountId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price
  ) {
    return OrderSessionCreateCommand.of(
        memberId,
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
