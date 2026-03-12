package com.fix.channel.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderSessionStateMachineTest {

  @Test
  void shouldCreatePendingNewOrderSessionAndExpireIt() {
    Instant expiresAt = Instant.parse("2026-03-12T00:10:00Z");
    OrderSession session = OrderSession.pendingNew(1L, "CL-CH-FSM-001", "ORD-REF-001", expiresAt);

    assertNotNull(session.getOrderSessionId());
    assertEquals(OrderSessionStatus.PENDING_NEW, session.getStatus());
    assertEquals(expiresAt, session.getExpiresAt());

    session.expire();

    assertEquals(OrderSessionStatus.EXPIRED, session.getStatus());
  }
}
