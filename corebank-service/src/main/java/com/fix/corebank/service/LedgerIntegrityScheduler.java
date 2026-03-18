package com.fix.corebank.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(
    value = "corebank.ledger-integrity.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class LedgerIntegrityScheduler {

  private final LedgerIntegrityService ledgerIntegrityService;

  public LedgerIntegrityScheduler(LedgerIntegrityService ledgerIntegrityService) {
    this.ledgerIntegrityService = ledgerIntegrityService;
  }

  @Scheduled(fixedDelayString = "${corebank.ledger-integrity.fixed-delay-ms:60000}")
  public void runIntegrityCheck() {
    try {
      ledgerIntegrityService.runCheckAndStore();
    } catch (RuntimeException ex) {
      log.warn("Scheduled ledger integrity check failed", ex);
    }
  }
}
