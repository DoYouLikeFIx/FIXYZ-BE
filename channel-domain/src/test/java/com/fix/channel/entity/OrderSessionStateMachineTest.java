package com.fix.channel.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class OrderSessionStateMachineTest {

  @Test
  void shouldCreatePendingNewOrderSessionAndExpireIt() {
    OrderSession session = OrderSession.pendingNew(1L, "CL-CH-FSM-001", "ORD-REF-001");

    assertNotNull(session.getOrderSessionId());
    assertEquals(OrderSessionStatus.PENDING_NEW, session.getStatus());

    session.expire();

    assertEquals(OrderSessionStatus.EXPIRED, session.getStatus());
  }
}
