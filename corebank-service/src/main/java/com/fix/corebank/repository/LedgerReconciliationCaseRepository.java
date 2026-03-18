package com.fix.corebank.repository;

import com.fix.corebank.entity.LedgerReconciliationCase;
import com.fix.corebank.entity.LedgerReconciliationCaseStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerReconciliationCaseRepository extends JpaRepository<LedgerReconciliationCase, Long> {

  Optional<LedgerReconciliationCase> findFirstByAnomalyIdAndStatusInOrderByIdDesc(
      Long anomalyId,
      Collection<LedgerReconciliationCaseStatus> statuses
  );

  Optional<LedgerReconciliationCase> findFirstByAnomalyIdOrderByIdDesc(Long anomalyId);
}
