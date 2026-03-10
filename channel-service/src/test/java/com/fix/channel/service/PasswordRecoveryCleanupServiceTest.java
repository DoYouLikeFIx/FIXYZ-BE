package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.channel.entity.PasswordResetTokenTerminalReason;
import com.fix.channel.repository.PasswordResetTokenRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PasswordRecoveryCleanupServiceTest {

  @Mock
  private PasswordResetTokenRepository passwordResetTokenRepository;

  private PasswordRecoveryProperties properties;
  private MutableClock clock;
  private SimpleMeterRegistry meterRegistry;
  private PasswordRecoveryCleanupService cleanupService;

  @BeforeEach
  void setUp() {
    properties = new PasswordRecoveryProperties();
    properties.getCleanup().setRetention(Duration.ofDays(30));
    properties.getCleanup().setBatchSize(2);
    properties.getCleanup().setMaxBatchesPerRun(8);
    properties.getCleanup().setMaxRunSeconds(1);
    properties.getCleanup().setBacklogAlertThreshold(3);

    clock = new MutableClock(Instant.parse("2026-03-11T00:00:00Z"));
    meterRegistry = new SimpleMeterRegistry();
    cleanupService = new PasswordRecoveryCleanupService(
        passwordResetTokenRepository,
        properties,
        meterRegistry,
        clock,
        new AtomicLong(),
        new AtomicLong(),
        new AtomicLong()
    );
  }

  @Test
  void shouldStopWhenRunTimeLimitIsExceededBeforeNextBatch() {
    when(passwordResetTokenRepository.countExpiredActiveTokens(any(Instant.class)))
        .thenReturn(2L, 0L);
    when(passwordResetTokenRepository.countTerminalPurgeEligibleTokens(any(Instant.class)))
        .thenReturn(0L, 0L);
    when(passwordResetTokenRepository.findExpiredActiveIdsForCleanup(any(Instant.class), eq(2)))
        .thenAnswer(invocation -> {
          clock.advance(Duration.ofMillis(1_100));
          return List.of(101L, 102L);
        });
    when(passwordResetTokenRepository.terminalizeExpiredTokens(
        eq(List.of(101L, 102L)),
        any(Instant.class),
        eq(PasswordResetTokenTerminalReason.EXPIRED),
        any(Instant.class)
    )).thenReturn(2);

    PasswordRecoveryCleanupService.CleanupSummary summary = cleanupService.runCleanupCycle();

    assertThat(summary.terminalizedCount()).isEqualTo(2);
    assertThat(summary.purgedCount()).isZero();
    assertThat(summary.batchesProcessed()).isEqualTo(1);
    assertThat(summary.haltedByTimeLimit()).isTrue();
    verify(passwordResetTokenRepository, never()).findTerminalPurgeCandidateIds(any(Instant.class), eq(2));
  }

  @Test
  void shouldEmitBacklogEvidenceAndUpdateMetrics(CapturedOutput output) {
    when(passwordResetTokenRepository.countExpiredActiveTokens(any(Instant.class)))
        .thenReturn(2L, 2L);
    when(passwordResetTokenRepository.countTerminalPurgeEligibleTokens(any(Instant.class)))
        .thenReturn(1L, 1L);
    when(passwordResetTokenRepository.findExpiredActiveIdsForCleanup(any(Instant.class), eq(2)))
        .thenReturn(List.of());
    when(passwordResetTokenRepository.findTerminalPurgeCandidateIds(any(Instant.class), eq(2)))
        .thenReturn(List.of());

    PasswordRecoveryCleanupService.CleanupSummary summary = cleanupService.runCleanupCycle();

    assertThat(summary.initialBacklogCount()).isEqualTo(3L);
    assertThat(summary.remainingBacklogCount()).isEqualTo(3L);
    assertThat(meterRegistry.get("auth.password_recovery.cleanup.backlog").gauge().value()).isEqualTo(3.0d);
    assertThat(meterRegistry.get("auth.password_recovery.cleanup.backlog.expired").gauge().value()).isEqualTo(2.0d);
    assertThat(meterRegistry.get("auth.password_recovery.cleanup.backlog.purge").gauge().value()).isEqualTo(1.0d);
    assertThat(output.getOut()).contains("password_reset_token_cleanup_backlog");
    assertThat(output.getOut()).contains("backlogCount=3");
    assertThat(output.getOut()).doesNotContain("raw-reset-token-secret");
  }

  private static final class MutableClock extends Clock {

    private Instant currentInstant;

    private MutableClock(Instant currentInstant) {
      this.currentInstant = currentInstant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return currentInstant;
    }

    private void advance(Duration duration) {
      currentInstant = currentInstant.plus(duration);
    }
  }
}
