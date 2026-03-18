package com.fix.corebank.repository;

import com.fix.corebank.entity.LedgerReconciliationCaseEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerReconciliationCaseEventRepository extends JpaRepository<LedgerReconciliationCaseEvent, Long> {
  List<LedgerReconciliationCaseEvent> findByCaseIdOrderByIdAsc(Long caseId);
}
