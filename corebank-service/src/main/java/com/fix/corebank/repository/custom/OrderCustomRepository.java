package com.fix.corebank.repository.custom;

import com.fix.corebank.entity.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface OrderCustomRepository {
  boolean existsByClOrdId(String clOrdId);

  List<Order> findPreviewRestingLimitOrdersForSweep(String symbol, String side, List<String> statuses);

  List<Order> lockExecutionRestingLimitOrdersForSweep(String symbol, String side, List<String> statuses);

  List<Order> lockExecutionRestingLimitOrdersForSweepChunk(
      String symbol,
      String side,
      List<String> statuses,
      BigDecimal cursorPrice,
      Instant cursorCreatedAt,
      Long cursorId,
      int limit
  );
}
