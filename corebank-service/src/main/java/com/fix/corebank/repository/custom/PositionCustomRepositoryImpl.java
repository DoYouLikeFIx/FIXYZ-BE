package com.fix.corebank.repository.custom;

import com.fix.corebank.entity.Position;
import com.fix.corebank.entity.QPosition;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class PositionCustomRepositoryImpl implements PositionCustomRepository {

  private static final QPosition POSITION = QPosition.position;
  private static final String JAKARTA_LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout";
  private static final String JAVAX_LOCK_TIMEOUT_HINT = "javax.persistence.lock.timeout";

  private final JPAQueryFactory queryFactory;
  private final int positionLockTimeoutMillis;

  public PositionCustomRepositoryImpl(
      JPAQueryFactory queryFactory,
      @Value("${corebank.order.position-lock-timeout-millis:1000}") int positionLockTimeoutMillis
  ) {
    this.queryFactory = queryFactory;
    this.positionLockTimeoutMillis = positionLockTimeoutMillis;
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
    JPAQuery<Position> query = queryFactory.selectFrom(POSITION)
        .where(POSITION.accountId.eq(accountId), POSITION.symbol.eq(symbol))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE);

    if (positionLockTimeoutMillis >= 0) {
      query.setHint(JAKARTA_LOCK_TIMEOUT_HINT, positionLockTimeoutMillis);
      query.setHint(JAVAX_LOCK_TIMEOUT_HINT, positionLockTimeoutMillis);
    }

    Position fetched = query.fetchOne();
    return Optional.ofNullable(fetched);
  }
}
