package com.fix.channel.service;

import com.fix.channel.entity.AuditLog;
import com.fix.channel.repository.AuditLogRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;
  private final TransactionOperations recordTransaction;

  public AuditLogService(
      AuditLogRepository auditLogRepository,
      PlatformTransactionManager transactionManager
  ) {
    this.auditLogRepository = auditLogRepository;
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.recordTransaction = transactionTemplate;
  }

  public AuditLog record(AuditLog auditLog) {
    try {
      AuditLog persisted = recordTransaction.execute(status -> auditLogRepository.saveAndFlush(auditLog));
      return persisted == null ? auditLog : persisted;
    } catch (RuntimeException ex) {
      log.error(
          "Failed to persist audit log action={} targetType={} targetId={} memberId={} orderSessionId={} correlationUuid={}",
          auditLog.getAction(),
          auditLog.getTargetType(),
          auditLog.getTargetId(),
          auditLog.getMemberId(),
          auditLog.getOrderSessionId(),
          auditLog.getCorrelationUuid(),
          ex
      );
      return auditLog;
    }
  }

  @Transactional
  public long purgeExpired(Instant cutoff) {
    return auditLogRepository.deleteByCreatedAtBefore(cutoff);
  }
}
