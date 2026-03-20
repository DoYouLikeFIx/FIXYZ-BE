package com.fix.corebank.repository;

import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerIntegrityAnomalyRecordRepository extends JpaRepository<LedgerIntegrityAnomalyRecord, Long> {
  List<LedgerIntegrityAnomalyRecord> findAllByRunId(Long runId);

  List<LedgerIntegrityAnomalyRecord> findAllByRunIdOrderByIdAsc(Long runId);

  List<LedgerIntegrityAnomalyRecord> findByRunIdOrderByIdAsc(Long runId, Pageable pageable);

  long countByRunId(Long runId);

  long countByRunIdAndTypeIn(Long runId, Collection<String> types);

  @Query("""
      select count(a)
      from LedgerIntegrityAnomalyRecord a
      where a.runId = :runId
        and not exists (
          select c.id
          from LedgerReconciliationCase c
          where c.anomalyId = a.id
        )
      """)
  long countUntrackedByRunId(@Param("runId") Long runId);

  @Query("""
      select count(a)
      from LedgerIntegrityAnomalyRecord a
      where a.runId = :runId
        and a.type in :types
        and not exists (
          select c.id
          from LedgerReconciliationCase c
          where c.anomalyId = a.id
        )
      """)
  long countUntrackedByRunIdAndTypeIn(
      @Param("runId") Long runId,
      @Param("types") Collection<String> types
  );

  @Query("""
      select a
      from LedgerIntegrityAnomalyRecord a
      where a.runId = :runId
        and not exists (
          select c.id
          from LedgerReconciliationCase c
          where c.anomalyId = a.id
        )
      order by a.id asc
      """)
  List<LedgerIntegrityAnomalyRecord> findUntrackedByRunIdOrderByIdAsc(
      @Param("runId") Long runId,
      Pageable pageable
  );

  @Query("""
      select a
      from LedgerIntegrityAnomalyRecord a
      where a.runId = :runId
        and a.type in :types
        and not exists (
          select c.id
          from LedgerReconciliationCase c
          where c.anomalyId = a.id
        )
      order by a.id asc
      """)
  List<LedgerIntegrityAnomalyRecord> findUntrackedByRunIdAndTypeInOrderByIdAsc(
      @Param("runId") Long runId,
      @Param("types") Collection<String> types,
      Pageable pageable
  );
}
