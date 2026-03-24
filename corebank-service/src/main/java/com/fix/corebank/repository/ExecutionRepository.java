package com.fix.corebank.repository;

import com.fix.corebank.entity.Execution;
import com.fix.corebank.repository.custom.ExecutionCustomRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, Long>, ExecutionCustomRepository {
  List<Execution> findAllByAccountIdAndSymbolOrderByExecutedAtAsc(Long accountId, String symbol);

  List<Execution> findAllByOrderIdOrderByExecutionSeqAsc(Long orderId);
}
