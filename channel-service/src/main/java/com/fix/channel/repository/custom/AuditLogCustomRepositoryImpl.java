package com.fix.channel.repository.custom;

import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.QAuditLog;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogCustomRepositoryImpl implements AuditLogCustomRepository {

  private static final QAuditLog AUDIT_LOG = QAuditLog.auditLog;

  private final JPAQueryFactory queryFactory;

  public AuditLogCustomRepositoryImpl(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Override
  public Page<AuditLog> findAdminAuditLogs(
      Pageable pageable,
      Instant from,
      Instant to,
      Long memberId,
      List<String> actions
  ) {
    BooleanBuilder where = new BooleanBuilder();

    if (from != null) {
      where.and(AUDIT_LOG.createdAt.goe(from));
    }
    if (to != null) {
      where.and(AUDIT_LOG.createdAt.loe(to));
    }
    if (memberId != null) {
      where.and(AUDIT_LOG.memberId.eq(memberId));
    }
    if (actions != null) {
      if (actions.isEmpty()) {
        where.and(AUDIT_LOG.id.isNull());
      } else {
        where.and(AUDIT_LOG.action.in(actions));
      }
    }

    List<AuditLog> content = queryFactory
        .selectFrom(AUDIT_LOG)
        .where(where)
        .orderBy(AUDIT_LOG.createdAt.desc())
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

    Long count = queryFactory
        .select(AUDIT_LOG.count())
        .from(AUDIT_LOG)
        .where(where)
        .fetchOne();

    long total = count == null ? 0L : count;
    return new PageImpl<>(content, pageable, total);
  }
}