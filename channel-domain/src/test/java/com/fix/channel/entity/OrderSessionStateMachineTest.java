package com.fix.channel.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderSessionStateMachineTest {

  @Test
  void shouldCreatePendingNewOrderSessionAndExpireIt() {
    OrderSession session = OrderSession.pendingNew(1L, "CL-CH-FSM-001", "ORD-REF-001");

    assertNotNull(session.getOrderSessionId());
    assertEquals(OrderSessionStatus.PENDING_NEW, session.getStatus());
    assertTrue(session.isChallengeRequired());
    assertEquals(OrderSessionAuthorizationReason.STEP_UP_REQUIRED, session.getAuthorizationReason());

    session.expire();

    assertEquals(OrderSessionStatus.EXPIRED, session.getStatus());
  }

  @Test
  void shouldCreateAutoAuthorizedOrderSession() {
    OrderSession session = OrderSession.authed(
        1L,
        "CL-CH-FSM-002",
        "ORD-REF-002",
        OrderSessionAuthorizationReason.LOGIN_MFA_FRESH
    );

    assertNotNull(session.getOrderSessionId());
    assertEquals(OrderSessionStatus.AUTHED, session.getStatus());
    assertFalse(session.isChallengeRequired());
    assertEquals(OrderSessionAuthorizationReason.LOGIN_MFA_FRESH, session.getAuthorizationReason());
  }
}
