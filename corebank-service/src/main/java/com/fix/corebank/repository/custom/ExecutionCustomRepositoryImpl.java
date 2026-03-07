package com.fix.corebank.repository.custom;

import com.fix.corebank.entity.QExecution;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
public class ExecutionCustomRepositoryImpl implements ExecutionCustomRepository {

  private static final QExecution EXECUTION = QExecution.execution;

  private final JPAQueryFactory queryFactory;

  public ExecutionCustomRepositoryImpl(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Override
  public BigDecimal sumSellQuantityByAccountAndSymbolBetween(Long accountId, String symbol, Instant from, Instant to) {
    BigDecimal result = queryFactory.select(EXECUTION.execQty.sum())
        .from(EXECUTION)
        .where(
            EXECUTION.accountId.eq(accountId),
            EXECUTION.symbol.eq(symbol),
            EXECUTION.side.eq("SELL"),
            EXECUTION.executedAt.goe(from),
            EXECUTION.executedAt.lt(to)
        )
        .fetchOne();
    return result == null ? BigDecimal.ZERO : result;
  }
}
