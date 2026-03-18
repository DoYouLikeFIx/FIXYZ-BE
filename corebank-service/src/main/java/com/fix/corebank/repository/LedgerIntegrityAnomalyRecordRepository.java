package com.fix.corebank.repository;

import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerIntegrityAnomalyRecordRepository extends JpaRepository<LedgerIntegrityAnomalyRecord, Long> {
  List<LedgerIntegrityAnomalyRecord> findAllByRunId(Long runId);
}
