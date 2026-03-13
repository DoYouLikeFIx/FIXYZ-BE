package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class MfaRecoveryTokenServiceTest {

  private MutableClock clock;
  private MfaRecoveryTokenService tokenService;

  @BeforeEach
  void setUp() {
    ObjectProvider<StringRedisTemplate> redisTemplateProvider = new ObjectProvider<>() {
      @Override
      public StringRedisTemplate getObject(Object... args) {
        return null;
      }

      @Override
      public StringRedisTemplate getObject() {
        return null;
      }

      @Override
      public StringRedisTemplate getIfAvailable() {
        return null;
      }

      @Override
      public StringRedisTemplate getIfUnique() {
        return null;
      }

      @Override
      public java.util.Iterator<StringRedisTemplate> iterator() {
        return Collections.emptyIterator();
      }
    };

    clock = new MutableClock(Instant.parse("2026-03-13T00:00:00Z"));
    tokenService = new MfaRecoveryTokenService(redisTemplateProvider, new ObjectMapper(), clock);
    ReflectionTestUtils.setField(tokenService, "recoveryProofTtl", Duration.ofMinutes(10));
    ReflectionTestUtils.setField(tokenService, "rebindTokenTtl", Duration.ofMinutes(10));
  }

  @Test
  void shouldPruneExpiredInMemoryEntriesBeforeIssuingNewProof() {
    Member firstMember = member(101L, "first@example.com");
    Member secondMember = member(202L, "second@example.com");

    MfaRecoveryTokenService.RecoveryProof expiredProof = tokenService.issueRecoveryProof(firstMember);
    MfaRecoveryTokenService.RebindTokenState expiredRebind = tokenService.issueRebindToken(firstMember);

    clock.advance(Duration.ofMinutes(11));

    MfaRecoveryTokenService.RecoveryProof freshProof = tokenService.issueRecoveryProof(secondMember);

    assertThat(proofs()).containsOnlyKeys(freshProof.recoveryProof());
    assertThat(proofs()).doesNotContainKey(expiredProof.recoveryProof());
    assertThat(proofIndex()).containsEntry(secondMember.getId(), freshProof.recoveryProof());
    assertThat(proofIndex()).doesNotContainKey(firstMember.getId());

    assertThat(rebindTokens()).doesNotContainKey(expiredRebind.rebindToken());
    assertThat(rebindTokens()).isEmpty();
    assertThat(rebindIndex()).doesNotContainKey(firstMember.getId());
    assertThat(rebindIndex()).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private Map<String, ?> proofs() {
    return (Map<String, ?>) ReflectionTestUtils.getField(tokenService, "inMemoryProofs");
  }

  @SuppressWarnings("unchecked")
  private Map<Long, String> proofIndex() {
    return (Map<Long, String>) ReflectionTestUtils.getField(tokenService, "inMemoryProofByMember");
  }

  @SuppressWarnings("unchecked")
  private Map<String, ?> rebindTokens() {
    return (Map<String, ?>) ReflectionTestUtils.getField(tokenService, "inMemoryRebindTokens");
  }

  @SuppressWarnings("unchecked")
  private Map<Long, String> rebindIndex() {
    return (Map<Long, String>) ReflectionTestUtils.getField(tokenService, "inMemoryRebindByMember");
  }

  private Member member(Long id, String email) {
    Member member = Member.registerUser("member-" + id, email, "hashed-password", "Tester");
    ReflectionTestUtils.setField(member, "id", id);
    return member;
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
