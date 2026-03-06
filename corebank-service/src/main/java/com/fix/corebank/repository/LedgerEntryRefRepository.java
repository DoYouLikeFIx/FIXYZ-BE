package com.fix.corebank.repository;

import com.fix.corebank.entity.LedgerEntryRef;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRefRepository extends JpaRepository<LedgerEntryRef, Long> {
}
