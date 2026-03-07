package com.fix.corebank.repository.custom;

import com.fix.corebank.entity.Position;
import com.fix.corebank.entity.QPosition;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PositionCustomRepositoryImpl implements PositionCustomRepository {

  private static final QPosition POSITION = QPosition.position;

  private final JPAQueryFactory queryFactory;

  public PositionCustomRepositoryImpl(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Override
  public Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol) {
    Position fetched = queryFactory.selectFrom(POSITION)
        .where(POSITION.accountId.eq(accountId), POSITION.symbol.eq(symbol))
        .fetchOne();
    return Optional.ofNullable(fetched);
  }

  @Override
  public Optional<Position> findByAccountIdAndSymbolForUpdate(Long accountId, String symbol) {
    Position fetched = queryFactory.selectFrom(POSITION)
        .where(POSITION.accountId.eq(accountId), POSITION.symbol.eq(symbol))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .fetchOne();
    return Optional.ofNullable(fetched);
  }
}
