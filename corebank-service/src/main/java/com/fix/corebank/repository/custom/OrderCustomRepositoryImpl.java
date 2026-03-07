package com.fix.corebank.repository.custom;

import com.fix.corebank.entity.QOrder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

@Repository
public class OrderCustomRepositoryImpl implements OrderCustomRepository {

  private static final QOrder ORDER = QOrder.order;

  private final JPAQueryFactory queryFactory;

  public OrderCustomRepositoryImpl(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Override
  public boolean existsByClOrdId(String clOrdId) {
    Integer fetched = queryFactory.selectOne()
        .from(ORDER)
        .where(ORDER.clOrdId.eq(clOrdId))
        .fetchFirst();
    return fetched != null;
  }
}
