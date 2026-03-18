package com.fix.corebank.repository;

import com.fix.corebank.entity.LedgerReconciliationCase;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerReconciliationCaseRepository extends JpaRepository<LedgerReconciliationCase, Long> {
  Optional<LedgerReconciliationCase> findFirstByAnomalyIdOrderByIdDesc(Long anomalyId);
}
