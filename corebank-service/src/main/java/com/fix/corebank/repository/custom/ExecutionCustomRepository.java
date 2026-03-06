package com.fix.corebank.repository.custom;

import java.math.BigDecimal;
import java.time.Instant;

public interface ExecutionCustomRepository {
  BigDecimal sumSellQuantityByAccountAndSymbolBetween(Long accountId, String symbol, Instant from, Instant to);
}
