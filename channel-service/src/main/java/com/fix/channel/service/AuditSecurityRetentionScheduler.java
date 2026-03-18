package com.fix.channel.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
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
