package com.fix.corebank.repository;

import com.fix.corebank.vo.LedgerIntegrityAnomaly;
import java.util.List;

public interface LedgerIntegrityQueryRepository {

  List<LedgerIntegrityAnomaly> findNegativePositions();

  List<LedgerIntegrityAnomaly> findOrphanExecutions();

  List<LedgerIntegrityAnomaly> findJournalLedgerCountMismatches();

  List<LedgerIntegrityAnomaly> findJournalLedgerBalanceMismatches();

  List<LedgerIntegrityAnomaly> findMissingLedgerClOrdReferences();
}
