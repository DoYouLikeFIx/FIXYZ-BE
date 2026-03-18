package com.fix.corebank.repository;

import com.fix.corebank.entity.LedgerReconciliationRepair;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerReconciliationRepairRepository extends JpaRepository<LedgerReconciliationRepair, Long> {
  Optional<LedgerReconciliationRepair> findByCaseIdAndRepairKey(Long caseId, String repairKey);

  Optional<LedgerReconciliationRepair> findFirstByCaseIdOrderByIdDesc(Long caseId);
}
