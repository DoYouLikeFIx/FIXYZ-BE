package com.fix.corebank.repository;

import com.fix.corebank.entity.LedgerReconciliationCase;
import com.fix.corebank.entity.LedgerReconciliationCaseStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerReconciliationCaseRepository extends JpaRepository<LedgerReconciliationCase, Long> {
  Optional<LedgerReconciliationCase> findFirstByAnomalyIdOrderByIdDesc(Long anomalyId);

  List<LedgerReconciliationCase> findAllByStatusInOrderByIdAsc(Collection<LedgerReconciliationCaseStatus> statuses);

  List<LedgerReconciliationCase> findAllByAnomalyIdIn(Collection<Long> anomalyIds);

  List<LedgerReconciliationCase> findByStatusInOrderByRunIdDescIdAsc(
      Collection<LedgerReconciliationCaseStatus> statuses,
      Pageable pageable
  );

  List<LedgerReconciliationCase> findByStatusInAndAnomalyTypeInOrderByRunIdDescIdAsc(
      Collection<LedgerReconciliationCaseStatus> statuses,
      Collection<String> anomalyTypes,
      Pageable pageable
  );

  long countByStatusIn(Collection<LedgerReconciliationCaseStatus> statuses);

  long countByStatus(LedgerReconciliationCaseStatus status);

  long countByStatusInAndAnomalyTypeIn(
      Collection<LedgerReconciliationCaseStatus> statuses,
      Collection<String> anomalyTypes
  );

  @Query("""
      select count(distinct c.runId)
      from LedgerReconciliationCase c
      where c.status in :statuses
      """)
  long countDistinctRunIdByStatusIn(@Param("statuses") Collection<LedgerReconciliationCaseStatus> statuses);

  @Query("""
      select count(distinct c.runId)
      from LedgerReconciliationCase c
      where c.status in :statuses
        and c.anomalyType in :anomalyTypes
      """)
  long countDistinctRunIdByStatusInAndAnomalyTypeIn(
      @Param("statuses") Collection<LedgerReconciliationCaseStatus> statuses,
      @Param("anomalyTypes") Collection<String> anomalyTypes
  );
}
