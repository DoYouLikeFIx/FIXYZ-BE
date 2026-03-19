package com.fix.channel.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    value = "channel.audit-security-retention.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AuditSecurityRetentionScheduler {

  private final AuditSecurityRetentionService auditSecurityRetentionService;

  public AuditSecurityRetentionScheduler(AuditSecurityRetentionService auditSecurityRetentionService) {
    this.auditSecurityRetentionService = auditSecurityRetentionService;
  }

  @Scheduled(fixedDelayString = "${channel.audit-security-retention.fixed-delay-ms:3600000}")
  public void cleanUpAuditAndSecurityEvents() {
    auditSecurityRetentionService.runCleanupCycle();
  }
}
