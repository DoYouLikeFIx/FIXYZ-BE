package com.fix.channel.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AuditSecurityRetentionService {

  private final AuditLogService auditLogService;
  private final SecurityEventService securityEventService;
  private final Clock clock;
  private final Duration auditRetention;
  private final Duration securityRetention;

  public AuditSecurityRetentionService(
      AuditLogService auditLogService,
      SecurityEventService securityEventService,
      Clock clock,
      @Value("${channel.audit.retention:90d}") Duration auditRetention,
      @Value("${channel.security-event.retention:180d}") Duration securityRetention
  ) {
    this.auditLogService = auditLogService;
    this.securityEventService = securityEventService;
    this.clock = clock;
    this.auditRetention = auditRetention;
    this.securityRetention = securityRetention;
  }

  @Transactional
  public CleanupResult runCleanupCycle() {
    Instant now = Instant.now(clock);
    long deletedAuditLogs = auditLogService.purgeExpired(now.minus(auditRetention));
    long deletedSecurityEvents = securityEventService.purgeExpired(now.minus(securityRetention));
    log.info(
        "Audit/security retention cleanup completed deletedAuditLogs={} deletedSecurityEvents={}",
        deletedAuditLogs,
        deletedSecurityEvents
    );
    return new CleanupResult(deletedAuditLogs, deletedSecurityEvents);
  }

  public record CleanupResult(long deletedAuditLogs, long deletedSecurityEvents) {
  }
}
