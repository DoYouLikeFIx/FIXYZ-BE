package com.fix.channel.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSessionRecoverySchedulerTest {

  @Mock
  private OrderSessionRecoveryService orderSessionRecoveryService;

  @InjectMocks
  private OrderSessionRecoveryScheduler scheduler;

  @Test
  void shouldRunRecoveryCycle() {
    scheduler.runRecoveryCycle();
    verify(orderSessionRecoveryService).runRecoveryCycle();
  }

  @Test
  void shouldSwallowRuntimeExceptionsAndContinue() {
    doThrow(new IllegalStateException("boom"))
        .when(orderSessionRecoveryService)
        .runRecoveryCycle();

    scheduler.runRecoveryCycle();

    verify(orderSessionRecoveryService).runRecoveryCycle();
  }
}
