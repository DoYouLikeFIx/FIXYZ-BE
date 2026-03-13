package com.fix.channel.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderSessionStateMachineTest {

  @Test
  void shouldCreatePendingNewOrderSessionAndExpireIt() {
    Instant expiresAt = Instant.parse("2026-03-12T00:10:00Z");
    OrderSession session = OrderSession.initiated(
        1L,
        101L,
        "123e4567-e89b-42d3-a456-426614174260",
        "fingerprint",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        true,
        "ELEVATED_ORDER_RISK",
        expiresAt
    );

    assertNotNull(session.getOrderSessionId());
    assertEquals(OrderSessionStatus.PENDING_NEW, session.getStatus());
    assertEquals(101L, session.getAccountId());
    assertEquals("005930", session.getSymbol());
    assertEquals("BUY", session.getSide());
    assertEquals("LIMIT", session.getOrderType());
    assertEquals(expiresAt, session.getExpiresAt());

    session.expire();

    assertEquals(OrderSessionStatus.EXPIRED, session.getStatus());
  }
}
