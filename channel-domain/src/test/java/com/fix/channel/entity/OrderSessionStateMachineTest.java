package com.fix.channel.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrderSessionStateMachineTest {

  @Test
  void shouldTransitionOrderSessionFromOpenToClosed() {
    OrderSession session = OrderSession.open(1L, "CL-CH-FSM-001", "ORD-REF-001");

    assertEquals("OPEN", session.getStatus());

    session.close();

    assertEquals("CLOSED", session.getStatus());
  }
}
