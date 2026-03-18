package com.fix.corebank.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class LedgerIntegritySchedulerTest {

  @Test
  void shouldInvokeIntegrityPersistenceRun() {
    LedgerIntegrityService ledgerIntegrityService = mock(LedgerIntegrityService.class);
    LedgerIntegrityScheduler scheduler = new LedgerIntegrityScheduler(ledgerIntegrityService);

    scheduler.runIntegrityCheck();

    verify(ledgerIntegrityService, times(1)).runCheckAndStore();
  }

  @Test
  void shouldSwallowRuntimeExceptionFromIntegrityRun() {
    LedgerIntegrityService ledgerIntegrityService = mock(LedgerIntegrityService.class);
    doThrow(new IllegalStateException("simulated scheduler failure"))
        .when(ledgerIntegrityService)
        .runCheckAndStore();
    LedgerIntegrityScheduler scheduler = new LedgerIntegrityScheduler(ledgerIntegrityService);

    scheduler.runIntegrityCheck();

    verify(ledgerIntegrityService, times(1)).runCheckAndStore();
  }
}
