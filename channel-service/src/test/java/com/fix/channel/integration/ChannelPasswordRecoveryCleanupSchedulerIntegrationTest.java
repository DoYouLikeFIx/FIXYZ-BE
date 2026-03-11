package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.channel.entity.Member;
import com.fix.channel.entity.PasswordResetToken;
import com.fix.channel.entity.PasswordResetTokenTerminalReason;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.PasswordResetTokenRepository;
import com.fix.channel.service.PasswordRecoveryCleanupScheduler;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext
class ChannelPasswordRecoveryCleanupSchedulerIntegrationTest extends ChannelContainersIntegrationTestBase {

  @Autowired
  private PasswordRecoveryCleanupScheduler passwordRecoveryCleanupScheduler;

  @Autowired
  private ScheduledTaskHolder scheduledTaskHolder;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private PasswordResetTokenRepository passwordResetTokenRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    passwordResetTokenRepository.deleteAll();
    memberRepository.deleteAll();
  }

  @Test
  void shouldRegisterCleanupSchedulerWithFixedRateCadenceAndFireAgainstExpiredTokens() throws Exception {
    Method scheduledMethod = PasswordRecoveryCleanupScheduler.class.getMethod("cleanUpPasswordResetTokens");
    Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);

    assertThat(passwordRecoveryCleanupScheduler).isNotNull();
    assertThat(scheduled).isNotNull();
    assertThat(scheduled.fixedRateString()).isEqualTo("#{@passwordRecoveryCleanupCadenceMillis}");
    assertThat(scheduled.initialDelayString()).isEqualTo("#{@passwordRecoveryCleanupCadenceMillis}");
    ScheduledTask cleanupTask = scheduledTaskHolder.getScheduledTasks().stream()
        .filter(task -> task.toString().contains(PasswordRecoveryCleanupScheduler.class.getName())
            && task.toString().contains(scheduledMethod.getName()))
        .findFirst()
        .orElseThrow();
    assertThat(cleanupTask.nextExecution()).isNotNull();

    Instant now = Instant.now();
    Member member = memberRepository.save(
        Member.registerUser(
            "M-PRC-SCH-001",
            "scheduler.cleanup@fixyz.com",
            passwordEncoder.encode("Abcd1234!"),
            "Scheduler Cleanup"
        )
    );
    PasswordResetToken expiredToken = PasswordResetToken.issueActive(
        member.getId(),
        tokenHash(1),
        (short) 2,
        now.minus(Duration.ofHours(1)),
        now.minus(Duration.ofMinutes(1)),
        "127.0.0.1",
        tokenHash(101)
    );
    passwordResetTokenRepository.saveAndFlush(expiredToken);

    cleanupTask.getTask().getRunnable().run();

    PasswordResetToken persistedExpired = passwordResetTokenRepository.findAll().stream()
        .filter(token -> tokenHash(1).equals(token.getTokenHash()))
        .findFirst()
        .orElseThrow();

    assertThat(persistedExpired).isNotNull();
    assertThat(persistedExpired.getActiveSlot()).isNull();
    assertThat(persistedExpired.getTerminalReason()).isEqualTo(PasswordResetTokenTerminalReason.EXPIRED);
    assertThat(persistedExpired.getTerminalizedAt()).isNotNull();
  }

  private String tokenHash(long seed) {
    return "%064x".formatted(seed);
  }
}
