package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.channel.entity.Member;
import com.fix.channel.entity.PasswordResetToken;
import com.fix.channel.entity.PasswordResetTokenTerminalReason;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.PasswordResetTokenRepository;
import com.fix.channel.service.PasswordRecoveryCleanupService;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "auth.password-recovery.cleanup.batch-size=2",
    "auth.password-recovery.cleanup.max-batches-per-run=2",
    "auth.password-recovery.cleanup.max-run-seconds=20",
    "auth.password-recovery.cleanup.backlog-alert-threshold=1"
})
class ChannelPasswordResetCleanupIntegrationTest extends ChannelContainersIntegrationTestBase {

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private PasswordResetTokenRepository passwordResetTokenRepository;

  @Autowired
  private PasswordRecoveryCleanupService passwordRecoveryCleanupService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    passwordResetTokenRepository.deleteAll();
    memberRepository.deleteAll();
  }

  @Test
  void shouldTerminalizeExpiredActiveTokensAndLeaveValidTokensUntouched() {
    Instant now = Instant.now();
    Member expiredMember = saveMember("M-PRC-001", "expired.cleanup@fixyz.com");
    Member validMember = saveMember("M-PRC-002", "valid.cleanup@fixyz.com");

    PasswordResetToken expiredToken = PasswordResetToken.issueActive(
        expiredMember.getId(),
        tokenHash(1),
        (short) 2,
        now.minus(Duration.ofHours(2)),
        now.minus(Duration.ofMinutes(5)),
        "127.0.0.1",
        tokenHash(101)
    );
    PasswordResetToken validToken = PasswordResetToken.issueActive(
        validMember.getId(),
        tokenHash(2),
        (short) 2,
        now.minus(Duration.ofMinutes(5)),
        now.plus(Duration.ofMinutes(10)),
        "127.0.0.2",
        tokenHash(102)
    );

    passwordResetTokenRepository.saveAllAndFlush(List.of(expiredToken, validToken));
    Instant preCleanupUpdatedAt = expiredToken.getUpdatedAt();

    PasswordRecoveryCleanupService.CleanupSummary summary = passwordRecoveryCleanupService.runCleanupCycle();

    List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
    PasswordResetToken persistedExpired = findByTokenHash(tokens, tokenHash(1));
    PasswordResetToken persistedValid = findByTokenHash(tokens, tokenHash(2));

    assertThat(summary.terminalizedCount()).isEqualTo(1);
    assertThat(summary.purgedCount()).isZero();
    assertThat(persistedExpired.getActiveSlot()).isNull();
    assertThat(persistedExpired.getTerminalReason()).isEqualTo(PasswordResetTokenTerminalReason.EXPIRED);
    assertThat(persistedExpired.getTerminalizedAt()).isNotNull();
    assertThat(persistedExpired.getConsumedAt()).isNull();
    assertThat(persistedExpired.getUpdatedAt()).isNotEqualTo(preCleanupUpdatedAt);
    assertThat(persistedExpired.getUpdatedAt()).isEqualTo(persistedExpired.getTerminalizedAt());
    assertThat(persistedValid.getActiveSlot()).isEqualTo(PasswordResetToken.ACTIVE_SLOT);
    assertThat(persistedValid.getTerminalReason()).isNull();
    assertThat(persistedValid.getTerminalizedAt()).isNull();
  }

  @Test
  void shouldPurgeEligibleTerminalTokensInBoundedBatchesAndAllowIdempotentReruns() {
    Instant now = Instant.now();
    PasswordResetToken retainedToken = terminalToken(
        saveMember("M-PRC-010", "retained.cleanup@fixyz.com").getId(),
        10,
        now.minus(Duration.ofDays(5)),
        now.minus(Duration.ofDays(5))
    );
    PasswordResetToken validActiveToken = PasswordResetToken.issueActive(
        saveMember("M-PRC-011", "active.cleanup@fixyz.com").getId(),
        tokenHash(11),
        (short) 2,
        now.minus(Duration.ofMinutes(10)),
        now.plus(Duration.ofMinutes(10)),
        "127.0.0.11",
        tokenHash(111)
    );

    List<PasswordResetToken> oldTerminalTokens = List.of(
        terminalToken(saveMember("M-PRC-012", "old.cleanup.1@fixyz.com").getId(), 12, now.minus(Duration.ofDays(45)), now.minus(Duration.ofDays(31))),
        terminalToken(saveMember("M-PRC-013", "old.cleanup.2@fixyz.com").getId(), 13, now.minus(Duration.ofDays(44)), now.minus(Duration.ofDays(32))),
        terminalToken(saveMember("M-PRC-014", "old.cleanup.3@fixyz.com").getId(), 14, now.minus(Duration.ofDays(43)), now.minus(Duration.ofDays(33))),
        terminalToken(saveMember("M-PRC-015", "old.cleanup.4@fixyz.com").getId(), 15, now.minus(Duration.ofDays(42)), now.minus(Duration.ofDays(34))),
        terminalToken(saveMember("M-PRC-016", "old.cleanup.5@fixyz.com").getId(), 16, now.minus(Duration.ofDays(41)), now.minus(Duration.ofDays(35)))
    );

    passwordResetTokenRepository.saveAllAndFlush(List.of(
        retainedToken,
        validActiveToken,
        oldTerminalTokens.get(0),
        oldTerminalTokens.get(1),
        oldTerminalTokens.get(2),
        oldTerminalTokens.get(3),
        oldTerminalTokens.get(4)
    ));

    PasswordRecoveryCleanupService.CleanupSummary firstRun = passwordRecoveryCleanupService.runCleanupCycle();
    List<PasswordResetToken> afterFirstRun = passwordResetTokenRepository.findAll();

    assertThat(firstRun.purgedCount()).isEqualTo(4);
    assertThat(firstRun.batchesProcessed()).isEqualTo(2);
    assertThat(firstRun.remainingPurgeBacklog()).isEqualTo(1L);
    assertThat(afterFirstRun).hasSize(3);
    assertThat(afterFirstRun)
        .extracting(PasswordResetToken::getTokenHash)
        .contains(tokenHash(10), tokenHash(11), tokenHash(12));

    PasswordRecoveryCleanupService.CleanupSummary secondRun = passwordRecoveryCleanupService.runCleanupCycle();
    List<PasswordResetToken> afterSecondRun = passwordResetTokenRepository.findAll();

    assertThat(secondRun.purgedCount()).isEqualTo(1);
    assertThat(secondRun.remainingBacklogCount()).isZero();
    assertThat(afterSecondRun).hasSize(2);
    assertThat(afterSecondRun)
        .extracting(PasswordResetToken::getTokenHash)
        .containsExactlyInAnyOrder(tokenHash(10), tokenHash(11));

    PasswordRecoveryCleanupService.CleanupSummary thirdRun = passwordRecoveryCleanupService.runCleanupCycle();
    PasswordResetToken persistedActive = findByTokenHash(afterSecondRun, tokenHash(11));
    PasswordResetToken persistedRetained = findByTokenHash(afterSecondRun, tokenHash(10));

    assertThat(thirdRun.terminalizedCount()).isZero();
    assertThat(thirdRun.purgedCount()).isZero();
    assertThat(persistedActive.getActiveSlot()).isEqualTo(PasswordResetToken.ACTIVE_SLOT);
    assertThat(persistedActive.getTerminalReason()).isNull();
    assertThat(persistedRetained.getTerminalReason()).isEqualTo(PasswordResetTokenTerminalReason.SUPERSEDED);
    assertThat(persistedRetained.getTerminalizedAt()).isNotNull();
  }

  private Member saveMember(String memberNo, String email) {
    return memberRepository.save(
        Member.registerUser(memberNo, email, passwordEncoder.encode("Abcd1234!"), "Cleanup Test User")
    );
  }

  private PasswordResetToken terminalToken(
      Long memberId,
      long seed,
      Instant expiresAt,
      Instant terminalizedAt
  ) {
    PasswordResetToken token = PasswordResetToken.issueActive(
        memberId,
        tokenHash(seed),
        (short) 2,
        expiresAt.minus(Duration.ofMinutes(15)),
        expiresAt,
        "127.0.0.%d".formatted(seed),
        tokenHash(seed + 100)
    );
    token.supersede(terminalizedAt);
    return token;
  }

  private PasswordResetToken findByTokenHash(List<PasswordResetToken> tokens, String tokenHash) {
    return tokens.stream()
        .sorted(Comparator.comparing(PasswordResetToken::getId))
        .filter(token -> tokenHash.equals(token.getTokenHash()))
        .findFirst()
        .orElseThrow();
  }

  private String tokenHash(long seed) {
    return "%064x".formatted(seed);
  }
}
