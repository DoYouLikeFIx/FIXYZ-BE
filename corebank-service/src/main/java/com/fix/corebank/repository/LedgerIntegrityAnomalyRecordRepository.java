package com.fix.corebank.repository;

import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerIntegrityAnomalyRecordRepository extends JpaRepository<LedgerIntegrityAnomalyRecord, Long> {
}
