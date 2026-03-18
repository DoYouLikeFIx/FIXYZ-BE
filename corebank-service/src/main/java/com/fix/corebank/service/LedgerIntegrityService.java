package com.fix.corebank.service;

import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import com.fix.corebank.entity.LedgerIntegrityRun;
import com.fix.corebank.repository.LedgerIntegrityAnomalyRecordRepository;
import com.fix.corebank.repository.LedgerIntegrityQueryRepository;
import com.fix.corebank.repository.LedgerIntegrityRunRepository;
import com.fix.corebank.vo.LedgerIntegrityAnomaly;
import com.fix.corebank.vo.LedgerIntegrityCheckResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LedgerIntegrityService {

  private final LedgerIntegrityQueryRepository ledgerIntegrityQueryRepository;
  private final LedgerIntegrityRunRepository ledgerIntegrityRunRepository;
  private final LedgerIntegrityAnomalyRecordRepository ledgerIntegrityAnomalyRecordRepository;

  public LedgerIntegrityService(
      LedgerIntegrityQueryRepository ledgerIntegrityQueryRepository,
      LedgerIntegrityRunRepository ledgerIntegrityRunRepository,
      LedgerIntegrityAnomalyRecordRepository ledgerIntegrityAnomalyRecordRepository
  ) {
    this.ledgerIntegrityQueryRepository = ledgerIntegrityQueryRepository;
    this.ledgerIntegrityRunRepository = ledgerIntegrityRunRepository;
    this.ledgerIntegrityAnomalyRecordRepository = ledgerIntegrityAnomalyRecordRepository;
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

  public LedgerIntegrityCheckResult runCheckAndStore() {
    LedgerIntegrityCheckResult result = runCheck();
    LedgerIntegrityRun savedRun = ledgerIntegrityRunRepository.save(
        LedgerIntegrityRun.of(
            result.getCheckedAt(),
            result.isPassed(),
            result.getAnomalyCount(),
            summarize(result.getAnomalies())
        )
    );

    if (!result.getAnomalies().isEmpty()) {
      ledgerIntegrityAnomalyRecordRepository.saveAll(
          result.getAnomalies().stream()
              .map(anomaly -> LedgerIntegrityAnomalyRecord.of(
                  savedRun.getId(),
                  anomaly.getType(),
                  anomaly.getMessage(),
                  anomaly.getAccountId(),
                  anomaly.getSymbol(),
                  anomaly.getPositionId(),
                  anomaly.getExecutionId(),
                  anomaly.getOrderId(),
                  anomaly.getClOrdId(),
                  anomaly.getJournalEntryId(),
                  anomaly.getLedgerEntryId()
              ))
              .toList()
      );
    }

    return result;
  }

  private String summarize(List<LedgerIntegrityAnomaly> anomalies) {
    if (anomalies.isEmpty()) {
      return "Ledger integrity check passed";
    }

    LedgerIntegrityAnomaly first = anomalies.get(0);
    if (anomalies.size() == 1) {
      return first.getType() + ": " + first.getMessage();
    }
    return first.getType() + ": " + first.getMessage() + " (+" + (anomalies.size() - 1) + " more)";
  }
}
