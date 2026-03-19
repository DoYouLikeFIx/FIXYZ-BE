package com.fix.channel.service;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.repository.OrderSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSessionRecoveryAttemptStore {

  private final OrderSessionRepository orderSessionRepository;
  private final Clock clock;
  private final Duration initialBackoff;
  private final Duration maxBackoff;

  public OrderSessionRecoveryAttemptStore(
      OrderSessionRepository orderSessionRepository,
      Clock clock,
      @Value("${order.session.recovery.backoff.initial:60s}") Duration initialBackoff,
      @Value("${order.session.recovery.backoff.max:15m}") Duration maxBackoff
  ) {
    this.orderSessionRepository = orderSessionRepository;
    this.clock = clock;
    Duration sanitizedInitialBackoff = initialBackoff == null || initialBackoff.isNegative()
        ? Duration.ZERO
        : initialBackoff;
    Duration sanitizedMaxBackoff = maxBackoff == null || maxBackoff.isNegative()
        ? sanitizedInitialBackoff
        : maxBackoff;
    this.initialBackoff = sanitizedInitialBackoff;
    this.maxBackoff = sanitizedMaxBackoff.compareTo(sanitizedInitialBackoff) < 0
        ? sanitizedInitialBackoff
        : sanitizedMaxBackoff;
  }

  @Transactional
  public AttemptReservation reserveAttempt(String orderSessionId) {
    OrderSession session = orderSessionRepository.findByOrderSessionId(orderSessionId)
        .orElse(null);
    if (session == null) {
      return null;
    }

    Instant now = Instant.now(clock);
    if (!session.isRecoveryAttemptEligible(now)) {
      return null;
    }

    int nextAttemptCount = session.getRecoveryAttemptCount() == null ? 1 : session.getRecoveryAttemptCount() + 1;
    Instant nextEligibleAt = now.plus(backoffFor(nextAttemptCount));
    session.reserveRecoveryAttempt(nextEligibleAt);
    orderSessionRepository.flush();
    return new AttemptReservation(nextAttemptCount, nextEligibleAt);
  }

  @Transactional
  public void clear(String orderSessionId) {
    orderSessionRepository.findByOrderSessionId(orderSessionId).ifPresent(session -> {
      session.clearRecoveryAttemptState();
      orderSessionRepository.flush();
    });
  }

  private Duration backoffFor(int attemptCount) {
    if (attemptCount < 1 || initialBackoff.isZero()) {
      return Duration.ZERO;
    }
    long multiplier = 1L;
    for (int remaining = 1; remaining < attemptCount; remaining += 1) {
      if (multiplier >= Long.MAX_VALUE / 2L) {
        return maxBackoff;
      }
      multiplier *= 2L;
    }
    try {
      Duration computed = initialBackoff.multipliedBy(multiplier);
      return computed.compareTo(maxBackoff) > 0 ? maxBackoff : computed;
    } catch (ArithmeticException ex) {
      return maxBackoff;
    }
  }

  public record AttemptReservation(int attemptCount, Instant nextEligibleAt) {
  }
}
