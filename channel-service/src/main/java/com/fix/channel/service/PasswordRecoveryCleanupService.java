package com.fix.channel.service;

import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.channel.entity.PasswordResetTokenTerminalReason;
import com.fix.channel.repository.PasswordResetTokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PasswordRecoveryCleanupService {

  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordRecoveryProperties properties;
  private final Clock clock;
  private final Counter terminalizedCounter;
  private final Counter purgedCounter;
  private final AtomicLong backlogGauge;
  private final AtomicLong expiredBacklogGauge;
  private final AtomicLong purgeBacklogGauge;

  @Autowired
  public PasswordRecoveryCleanupService(
      PasswordResetTokenRepository passwordResetTokenRepository,
      PasswordRecoveryProperties properties,
      MeterRegistry meterRegistry,
      Clock passwordRecoveryClock
  ) {
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.properties = properties;
    this.clock = passwordRecoveryClock;
    this.terminalizedCounter = Counter.builder("auth.password_recovery.cleanup.terminalized")
        .description("Password reset tokens terminalized by cleanup")
        .register(meterRegistry);
    this.purgedCounter = Counter.builder("auth.password_recovery.cleanup.purged")
        .description("Password reset tokens purged by cleanup")
        .register(meterRegistry);
    this.backlogGauge = meterRegistry.gauge("auth.password_recovery.cleanup.backlog", new AtomicLong());
    this.expiredBacklogGauge = meterRegistry.gauge("auth.password_recovery.cleanup.backlog.expired", new AtomicLong());
    this.purgeBacklogGauge = meterRegistry.gauge("auth.password_recovery.cleanup.backlog.purge", new AtomicLong());
  }

  PasswordRecoveryCleanupService(
      PasswordResetTokenRepository passwordResetTokenRepository,
      PasswordRecoveryProperties properties,
      MeterRegistry meterRegistry,
      Clock passwordRecoveryClock,
      AtomicLong backlogGauge,
      AtomicLong expiredBacklogGauge,
      AtomicLong purgeBacklogGauge
  ) {
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.properties = properties;
    this.clock = passwordRecoveryClock;
    this.terminalizedCounter = Counter.builder("auth.password_recovery.cleanup.terminalized")
        .register(meterRegistry);
    this.purgedCounter = Counter.builder("auth.password_recovery.cleanup.purged")
        .register(meterRegistry);
    this.backlogGauge = backlogGauge;
    this.expiredBacklogGauge = expiredBacklogGauge;
    this.purgeBacklogGauge = purgeBacklogGauge;
    meterRegistry.gauge("auth.password_recovery.cleanup.backlog", backlogGauge);
    meterRegistry.gauge("auth.password_recovery.cleanup.backlog.expired", expiredBacklogGauge);
    meterRegistry.gauge("auth.password_recovery.cleanup.backlog.purge", purgeBacklogGauge);
  }

  @Transactional
  public CleanupSummary runCleanupCycle() {
    PasswordRecoveryProperties.Cleanup cleanup = properties.getCleanup();
    Instant cycleStartedAt = Instant.now(clock);
    Duration retention = cleanup.getRetention();
    Duration maxRunTime = Duration.ofSeconds(cleanup.getMaxRunSeconds());

    long initialExpiredBacklog = passwordResetTokenRepository.countExpiredActiveTokens(cycleStartedAt);
    long initialPurgeBacklog = passwordResetTokenRepository.countTerminalPurgeEligibleTokens(cycleStartedAt.minus(retention));
    updateBacklogMetrics(initialExpiredBacklog, initialPurgeBacklog);
    logBacklogIfThresholdReached(initialExpiredBacklog, initialPurgeBacklog, cleanup.getBacklogAlertThreshold());

    int terminalizedCount = 0;
    int purgedCount = 0;
    int batchesProcessed = 0;
    boolean haltedByTimeLimit = false;

    while (batchesProcessed < cleanup.getMaxBatchesPerRun()) {
      if (Duration.between(cycleStartedAt, Instant.now(clock)).compareTo(maxRunTime) >= 0) {
        haltedByTimeLimit = true;
        break;
      }

      Instant batchReferenceTime = Instant.now(clock);
      List<Long> expiredIds = passwordResetTokenRepository.findExpiredActiveIdsForCleanup(
          batchReferenceTime,
          cleanup.getBatchSize()
      );
      if (!expiredIds.isEmpty()) {
        terminalizedCount += passwordResetTokenRepository.terminalizeExpiredTokens(
            expiredIds,
            batchReferenceTime,
            PasswordResetTokenTerminalReason.EXPIRED,
            batchReferenceTime
        );
        batchesProcessed++;
        continue;
      }

      Instant retentionCutoff = batchReferenceTime.minus(retention);
      List<Long> purgeIds = passwordResetTokenRepository.findTerminalPurgeCandidateIds(
          retentionCutoff,
          cleanup.getBatchSize()
      );
      if (purgeIds.isEmpty()) {
        break;
      }

      purgedCount += passwordResetTokenRepository.deleteTerminalizedTokensByIds(purgeIds, retentionCutoff);
      batchesProcessed++;
    }

    if (terminalizedCount > 0) {
      terminalizedCounter.increment(terminalizedCount);
    }
    if (purgedCount > 0) {
      purgedCounter.increment(purgedCount);
    }

    Instant cycleFinishedAt = Instant.now(clock);
    long remainingExpiredBacklog = passwordResetTokenRepository.countExpiredActiveTokens(cycleFinishedAt);
    long remainingPurgeBacklog = passwordResetTokenRepository.countTerminalPurgeEligibleTokens(cycleFinishedAt.minus(retention));
    updateBacklogMetrics(remainingExpiredBacklog, remainingPurgeBacklog);

    return new CleanupSummary(
        terminalizedCount,
        purgedCount,
        batchesProcessed,
        initialExpiredBacklog,
        initialPurgeBacklog,
        remainingExpiredBacklog,
        remainingPurgeBacklog,
        haltedByTimeLimit
    );
  }

  private void updateBacklogMetrics(long expiredBacklog, long purgeBacklog) {
    expiredBacklogGauge.set(expiredBacklog);
    purgeBacklogGauge.set(purgeBacklog);
    backlogGauge.set(expiredBacklog + purgeBacklog);
  }

  private void logBacklogIfThresholdReached(long expiredBacklog, long purgeBacklog, long backlogAlertThreshold) {
    long backlogCount = expiredBacklog + purgeBacklog;
    if (backlogCount < backlogAlertThreshold) {
      return;
    }

    log.warn(
        "password_reset_token_cleanup_backlog backlogCount={} threshold={} expiredActiveCount={} purgeEligibleCount={}",
        backlogCount,
        backlogAlertThreshold,
        expiredBacklog,
        purgeBacklog
    );
  }

  public record CleanupSummary(
      int terminalizedCount,
      int purgedCount,
      int batchesProcessed,
      long initialExpiredBacklog,
      long initialPurgeBacklog,
      long remainingExpiredBacklog,
      long remainingPurgeBacklog,
      boolean haltedByTimeLimit
  ) {
    public long initialBacklogCount() {
      return initialExpiredBacklog + initialPurgeBacklog;
    }

    public long remainingBacklogCount() {
      return remainingExpiredBacklog + remainingPurgeBacklog;
    }
  }
}
