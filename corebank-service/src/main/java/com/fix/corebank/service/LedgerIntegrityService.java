package com.fix.corebank.service;

import com.fix.corebank.repository.LedgerIntegrityQueryRepository;
import com.fix.corebank.vo.LedgerIntegrityAnomaly;
import com.fix.corebank.vo.LedgerIntegrityCheckResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LedgerIntegrityService {

  private final LedgerIntegrityQueryRepository ledgerIntegrityQueryRepository;

  public LedgerIntegrityService(LedgerIntegrityQueryRepository ledgerIntegrityQueryRepository) {
    this.ledgerIntegrityQueryRepository = ledgerIntegrityQueryRepository;
  }

  public LedgerIntegrityCheckResult runCheck() {
    List<LedgerIntegrityAnomaly> anomalies = new ArrayList<>();
    anomalies.addAll(ledgerIntegrityQueryRepository.findNegativePositions());
    anomalies.addAll(ledgerIntegrityQueryRepository.findOrphanExecutions());
    anomalies.addAll(ledgerIntegrityQueryRepository.findJournalLedgerCountMismatches());
    anomalies.addAll(ledgerIntegrityQueryRepository.findJournalLedgerBalanceMismatches());
    anomalies.addAll(ledgerIntegrityQueryRepository.findMissingLedgerClOrdReferences());
    return LedgerIntegrityCheckResult.of(Instant.now(), anomalies);
  }
}
