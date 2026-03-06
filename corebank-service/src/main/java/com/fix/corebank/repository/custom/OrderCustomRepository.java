package com.fix.corebank.repository.custom;

public interface OrderCustomRepository {
  boolean existsByClOrdId(String clOrdId);
}
