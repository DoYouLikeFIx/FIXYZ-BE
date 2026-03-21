package com.fix.corebank.repository.custom;

import com.fix.corebank.entity.Order;
import com.fix.corebank.entity.QOrder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class OrderCustomRepositoryImpl implements OrderCustomRepository {

  private static final QOrder ORDER = QOrder.order;
  private static final String JAKARTA_LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout";

  private final JPAQueryFactory queryFactory;
  private final int bookLockTimeoutMillis;

  public OrderCustomRepositoryImpl(
      JPAQueryFactory queryFactory,
      @Value("${corebank.order.book-lock-timeout-millis:1000}") int bookLockTimeoutMillis
  ) {
    this.queryFactory = queryFactory;
    this.bookLockTimeoutMillis = bookLockTimeoutMillis;
  }

  @Override
  public boolean existsByClOrdId(String clOrdId) {
    Integer fetched = queryFactory.selectOne()
        .from(ORDER)
        .where(ORDER.clOrdId.eq(clOrdId))
        .fetchFirst();
    return fetched != null;
  }

  @Override
  public List<Order> findRestingLimitOrdersForSweep(String symbol, String side, List<String> statuses) {
    JPAQuery<Order> query = queryFactory.selectFrom(ORDER)
        .where(
            ORDER.symbol.eq(symbol),
            ORDER.side.eq(side),
            ORDER.orderType.eq("LIMIT"),
            ORDER.orderPrice.isNotNull(),
            ORDER.status.in(statuses),
            remainingQuantityPositive()
        )
        .orderBy(orderSpecifiersFor(side))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE);

    if (bookLockTimeoutMillis >= 0) {
      query.setHint(JAKARTA_LOCK_TIMEOUT_HINT, bookLockTimeoutMillis);
    }

    return query.fetch();
  }

  private BooleanExpression remainingQuantityPositive() {
    return ORDER.leavesQty.isNotNull().and(ORDER.leavesQty.gt(BigDecimal.ZERO))
        .or(
            ORDER.leavesQty.isNull()
                .and(ORDER.orderQty.subtract(ORDER.executedQty.coalesce(BigDecimal.ZERO)).gt(BigDecimal.ZERO))
        );
  }

  private OrderSpecifier<?>[] orderSpecifiersFor(String side) {
    if ("SELL".equals(side)) {
      return new OrderSpecifier<?>[] {
          ORDER.orderPrice.asc(),
          ORDER.createdAt.asc(),
          ORDER.id.asc()
      };
    }
    if ("BUY".equals(side)) {
      return new OrderSpecifier<?>[] {
          ORDER.orderPrice.desc(),
          ORDER.createdAt.asc(),
          ORDER.id.asc()
      };
    }
    throw new IllegalArgumentException("side must be BUY or SELL");
  }
}
