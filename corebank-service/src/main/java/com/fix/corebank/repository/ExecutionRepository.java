package com.fix.corebank.repository;

import com.fix.corebank.entity.Execution;
import com.fix.corebank.repository.custom.ExecutionCustomRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, Long>, ExecutionCustomRepository {
}
