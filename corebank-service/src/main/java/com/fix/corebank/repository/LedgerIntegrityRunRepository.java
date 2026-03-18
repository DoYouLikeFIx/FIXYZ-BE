package com.fix.corebank.repository;

import com.fix.corebank.entity.LedgerIntegrityRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerIntegrityRunRepository extends JpaRepository<LedgerIntegrityRun, Long> {
}
