package com.fix.channel.service;

import com.fix.channel.entity.SecurityEvent;
import com.fix.channel.repository.SecurityEventRepository;
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
public class SecurityEventService {

  private final SecurityEventRepository securityEventRepository;
  private final TransactionOperations recordTransaction;

  public SecurityEventService(
      SecurityEventRepository securityEventRepository,
      PlatformTransactionManager transactionManager
  ) {
    this.securityEventRepository = securityEventRepository;
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.recordTransaction = transactionTemplate;
  }

  public SecurityEvent record(SecurityEvent securityEvent) {
    try {
      SecurityEvent persisted = recordTransaction.execute(status -> securityEventRepository.saveAndFlush(securityEvent));
      return persisted == null ? securityEvent : persisted;
    } catch (RuntimeException ex) {
      log.error(
          "Failed to persist security event type={} status={} memberId={} adminMemberId={} orderSessionId={} correlationUuid={}",
          securityEvent.getEventType(),
          securityEvent.getStatus(),
          securityEvent.getMemberId(),
          securityEvent.getAdminMemberId(),
          securityEvent.getOrderSessionId(),
          securityEvent.getCorrelationUuid(),
          ex
      );
      return securityEvent;
    }
  }

  @Transactional
  public long purgeExpired(Instant cutoff) {
    return securityEventRepository.deleteByOccurredAtBefore(cutoff);
  }
}
