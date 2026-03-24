package com.fix.corebank.repository;

import com.fix.corebank.entity.Execution;
import com.fix.corebank.repository.custom.ExecutionCustomRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, Long>, ExecutionCustomRepository {
  long countByOrderId(Long orderId);

  List<Execution> findAllByAccountIdAndSymbolOrderByExecutedAtAsc(Long accountId, String symbol);

  List<Execution> findAllByAccountIdAndSymbolInOrderBySymbolAscExecutedAtAscIdAsc(Long accountId, List<String> symbols);

  List<Execution> findAllByAccountIdAndSymbolInAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderBySymbolAscExecutedAtAscIdAsc(
      Long accountId,
      List<String> symbols,
      Instant executedAtFrom,
      Instant executedAtTo
  );

  List<Execution> findAllByOrderIdOrderByExecutionSeqAsc(Long orderId);
}
