package com.fix.channel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.Member;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MfaRecoveryTokenService {

  private static final String RECOVERY_PROOF_KEY_PREFIX = "ch:mfa-recovery:proof:";
  private static final String RECOVERY_PROOF_MEMBER_KEY_PREFIX = "ch:mfa-recovery:proof:member:";
  private static final String REBIND_TOKEN_KEY_PREFIX = "ch:mfa-recovery:rebind:";
  private static final String REBIND_TOKEN_MEMBER_KEY_PREFIX = "ch:mfa-recovery:rebind:member:";

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final ConcurrentMap<String, StoredRecoveryProof> inMemoryProofs = new ConcurrentHashMap<>();
  private final ConcurrentMap<Long, String> inMemoryProofByMember = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, StoredRebindToken> inMemoryRebindTokens = new ConcurrentHashMap<>();
  private final ConcurrentMap<Long, String> inMemoryRebindByMember = new ConcurrentHashMap<>();

  @Value("${auth.mfa-recovery.proof-ttl:10m}")
  private Duration recoveryProofTtl;

  @Value("${auth.mfa-recovery.rebind-ttl:10m}")
  private Duration rebindTokenTtl;

  public MfaRecoveryTokenService(
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public RecoveryProof issueRecoveryProof(Member member) {
    Instant now = Instant.now(clock);
    pruneExpiredEntries(now);
    Instant expiresAt = now.plus(recoveryProofTtl);
    String recoveryProof = "recovery-" + UUID.randomUUID();

    supersedeRecoveryProof(member.getId(), now);
    StoredRecoveryProof storedRecoveryProof = new StoredRecoveryProof(recoveryProof, member.getId(), expiresAt, null);
    saveRecoveryProof(storedRecoveryProof);
    saveRecoveryProofMemberIndex(member.getId(), recoveryProof, expiresAt);

    return new RecoveryProof(recoveryProof, expiresAt);
  }

  public RecoveryProofState consumeRecoveryProof(String recoveryProof) {
    pruneExpiredEntries(Instant.now(clock));
    RecoveryProofState recoveryProofState = requireActiveRecoveryProof(recoveryProof);
    String normalizedProof = recoveryProofState.recoveryProof();
    Instant now = Instant.now(clock);
    StoredRecoveryProof storedRecoveryProof = readRecoveryProof(normalizedProof);
    if (storedRecoveryProof == null || storedRecoveryProof.isExpiredAt(now)) {
      deleteRecoveryProof(normalizedProof, recoveryProofState.memberId());
      throw invalidRecoveryToken();
    }

    StoredRecoveryProof consumedProof = storedRecoveryProof.consumeAt(now);
    saveRecoveryProof(consumedProof);
    clearRecoveryProofIndexIfMatches(consumedProof.memberId(), normalizedProof);
    return consumedProof.toState();
  }

  public RecoveryProofState requireActiveRecoveryProof(String recoveryProof) {
    String normalizedProof = normalize(recoveryProof);
    if (normalizedProof.isBlank()) {
      throw invalidRecoveryToken();
    }

    Instant now = Instant.now(clock);
    pruneExpiredEntries(now);
    StoredRecoveryProof storedRecoveryProof = readRecoveryProof(normalizedProof);
    if (storedRecoveryProof == null || storedRecoveryProof.isExpiredAt(now)) {
      deleteRecoveryProof(normalizedProof, storedRecoveryProof == null ? null : storedRecoveryProof.memberId());
      throw invalidRecoveryToken();
    }
    if (storedRecoveryProof.isConsumed()) {
      throw consumedRecoveryToken();
    }
    return storedRecoveryProof.toState();
  }

  public RebindTokenState issueRebindToken(Member member) {
    Instant now = Instant.now(clock);
    pruneExpiredEntries(now);
    Instant expiresAt = now.plus(rebindTokenTtl);
    String rebindToken = "rebind-" + UUID.randomUUID();

    supersedeRebindToken(member.getId(), now);
    StoredRebindToken storedRebindToken = new StoredRebindToken(rebindToken, member.getId(), expiresAt, null);
    saveRebindToken(storedRebindToken);
    saveRebindMemberIndex(member.getId(), rebindToken, expiresAt);

    return storedRebindToken.toState();
  }

  public RebindTokenState requireActiveRebindToken(String rebindToken) {
    String normalizedToken = normalize(rebindToken);
    if (normalizedToken.isBlank()) {
      throw invalidRecoveryToken();
    }

    Instant now = Instant.now(clock);
    pruneExpiredEntries(now);
    StoredRebindToken storedRebindToken = readRebindToken(normalizedToken);
    if (storedRebindToken == null || storedRebindToken.isExpiredAt(now)) {
      deleteRebindToken(normalizedToken, storedRebindToken == null ? null : storedRebindToken.memberId());
      throw invalidRecoveryToken();
    }
    if (storedRebindToken.isConsumed()) {
      throw consumedRecoveryToken();
    }
    return storedRebindToken.toState();
  }

  public RebindTokenState consumeRebindToken(String rebindToken) {
    pruneExpiredEntries(Instant.now(clock));
    RebindTokenState rebindTokenState = requireActiveRebindToken(rebindToken);
    Instant now = Instant.now(clock);
    StoredRebindToken storedRebindToken = readRebindToken(rebindTokenState.rebindToken());
    if (storedRebindToken == null || storedRebindToken.isExpiredAt(now)) {
      deleteRebindToken(rebindTokenState.rebindToken(), rebindTokenState.memberId());
      throw invalidRecoveryToken();
    }
    if (storedRebindToken.isConsumed()) {
      throw consumedRecoveryToken();
    }

    StoredRebindToken consumedToken = storedRebindToken.consumeAt(now);
    saveRebindToken(consumedToken);
    clearRebindIndexIfMatches(consumedToken.memberId(), consumedToken.rebindToken());
    return consumedToken.toState();
  }

  public void discardRebindToken(String rebindToken) {
    String normalizedToken = normalize(rebindToken);
    if (normalizedToken.isBlank()) {
      return;
    }

    pruneExpiredEntries(Instant.now(clock));
    StoredRebindToken storedRebindToken = readRebindToken(normalizedToken);
    deleteRebindToken(normalizedToken, storedRebindToken == null ? null : storedRebindToken.memberId());
  }

  public long recoveryProofTtlSeconds() {
    return Math.max(1L, recoveryProofTtl.getSeconds());
  }

  private void pruneExpiredEntries(Instant now) {
    pruneProofEntries(now);
    pruneRebindEntries(now);
  }

  private void pruneProofEntries(Instant now) {
    inMemoryProofs.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    inMemoryProofByMember.entrySet().removeIf(entry -> {
      String proofId = entry.getValue();
      if (proofId == null) {
        return true;
      }
      return isExpired(inMemoryProofs.get(proofId), now);
    });
  }

  private void pruneRebindEntries(Instant now) {
    inMemoryRebindTokens.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    inMemoryRebindByMember.entrySet().removeIf(entry -> {
      String tokenId = entry.getValue();
      if (tokenId == null) {
        return true;
      }
      return isExpired(inMemoryRebindTokens.get(tokenId), now);
    });
  }

  private boolean isExpired(StoredRecoveryProof storedRecoveryProof, Instant now) {
    return storedRecoveryProof == null || storedRecoveryProof.isExpiredAt(now);
  }

  private boolean isExpired(StoredRebindToken storedRebindToken, Instant now) {
    return storedRebindToken == null || storedRebindToken.isExpiredAt(now);
  }

  private void supersedeRecoveryProof(Long memberId, Instant now) {
    String currentProof = readRecoveryProofMemberIndex(memberId);
    if (currentProof == null || currentProof.isBlank()) {
      return;
    }

    StoredRecoveryProof storedRecoveryProof = readRecoveryProof(currentProof);
    if (storedRecoveryProof == null || storedRecoveryProof.isConsumed() || storedRecoveryProof.isExpiredAt(now)) {
      deleteRecoveryProof(currentProof, memberId);
      return;
    }
    saveRecoveryProof(storedRecoveryProof.consumeAt(now));
    clearRecoveryProofIndexIfMatches(memberId, currentProof);
  }

  private void supersedeRebindToken(Long memberId, Instant now) {
    String currentToken = readRebindMemberIndex(memberId);
    if (currentToken == null || currentToken.isBlank()) {
      return;
    }

    StoredRebindToken storedRebindToken = readRebindToken(currentToken);
    if (storedRebindToken == null || storedRebindToken.isConsumed() || storedRebindToken.isExpiredAt(now)) {
      deleteRebindToken(currentToken, memberId);
      return;
    }
    saveRebindToken(storedRebindToken.consumeAt(now));
    clearRebindIndexIfMatches(memberId, currentToken);
  }

  private StoredRecoveryProof readRecoveryProof(String recoveryProof) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      String rawValue = redisTemplate.opsForValue().get(recoveryProofKey(recoveryProof));
      if (rawValue == null || rawValue.isBlank()) {
        return null;
      }
      try {
        StoredRecoveryProof storedRecoveryProof = objectMapper.readValue(rawValue, StoredRecoveryProof.class);
        if (storedRecoveryProof.isExpiredAt(Instant.now(clock))) {
          deleteRecoveryProof(storedRecoveryProof.recoveryProof(), storedRecoveryProof.memberId());
          return null;
        }
        return storedRecoveryProof;
      } catch (JsonProcessingException ex) {
        deleteRecoveryProof(recoveryProof, null);
        throw serializationFailure("mfa recovery proof deserialization failed", ex);
      }
    }

    StoredRecoveryProof storedRecoveryProof = inMemoryProofs.get(recoveryProof);
    if (storedRecoveryProof == null) {
      return null;
    }
    if (storedRecoveryProof.isExpiredAt(Instant.now(clock))) {
      deleteRecoveryProof(recoveryProof, storedRecoveryProof.memberId());
      return null;
    }
    return storedRecoveryProof;
  }

  private StoredRebindToken readRebindToken(String rebindToken) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      String rawValue = redisTemplate.opsForValue().get(rebindTokenKey(rebindToken));
      if (rawValue == null || rawValue.isBlank()) {
        return null;
      }
      try {
        StoredRebindToken storedRebindToken = objectMapper.readValue(rawValue, StoredRebindToken.class);
        if (storedRebindToken.isExpiredAt(Instant.now(clock))) {
          deleteRebindToken(storedRebindToken.rebindToken(), storedRebindToken.memberId());
          return null;
        }
        return storedRebindToken;
      } catch (JsonProcessingException ex) {
        deleteRebindToken(rebindToken, null);
        throw serializationFailure("mfa rebind token deserialization failed", ex);
      }
    }

    StoredRebindToken storedRebindToken = inMemoryRebindTokens.get(rebindToken);
    if (storedRebindToken == null) {
      return null;
    }
    if (storedRebindToken.isExpiredAt(Instant.now(clock))) {
      deleteRebindToken(rebindToken, storedRebindToken.memberId());
      return null;
    }
    return storedRebindToken;
  }

  private void saveRecoveryProof(StoredRecoveryProof storedRecoveryProof) {
    Duration ttl = remainingTtl(storedRecoveryProof.expiresAt());
    if (ttl.isZero() || ttl.isNegative()) {
      deleteRecoveryProof(storedRecoveryProof.recoveryProof(), storedRecoveryProof.memberId());
      return;
    }

    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      try {
        redisTemplate.opsForValue().set(
            recoveryProofKey(storedRecoveryProof.recoveryProof()),
            objectMapper.writeValueAsString(storedRecoveryProof),
            ttl
        );
        return;
      } catch (JsonProcessingException ex) {
        throw serializationFailure("mfa recovery proof serialization failed", ex);
      }
    }

    inMemoryProofs.put(storedRecoveryProof.recoveryProof(), storedRecoveryProof);
  }

  private void saveRebindToken(StoredRebindToken storedRebindToken) {
    Duration ttl = remainingTtl(storedRebindToken.expiresAt());
    if (ttl.isZero() || ttl.isNegative()) {
      deleteRebindToken(storedRebindToken.rebindToken(), storedRebindToken.memberId());
      return;
    }

    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      try {
        redisTemplate.opsForValue().set(
            rebindTokenKey(storedRebindToken.rebindToken()),
            objectMapper.writeValueAsString(storedRebindToken),
            ttl
        );
        return;
      } catch (JsonProcessingException ex) {
        throw serializationFailure("mfa rebind token serialization failed", ex);
      }
    }

    inMemoryRebindTokens.put(storedRebindToken.rebindToken(), storedRebindToken);
  }

  private void saveRecoveryProofMemberIndex(Long memberId, String recoveryProof, Instant expiresAt) {
    Duration ttl = remainingTtl(expiresAt);
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      redisTemplate.opsForValue().set(recoveryProofMemberKey(memberId), recoveryProof, ttl);
      return;
    }
    inMemoryProofByMember.put(memberId, recoveryProof);
  }

  private void saveRebindMemberIndex(Long memberId, String rebindToken, Instant expiresAt) {
    Duration ttl = remainingTtl(expiresAt);
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      redisTemplate.opsForValue().set(rebindMemberKey(memberId), rebindToken, ttl);
      return;
    }
    inMemoryRebindByMember.put(memberId, rebindToken);
  }

  private String readRecoveryProofMemberIndex(Long memberId) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      return redisTemplate.opsForValue().get(recoveryProofMemberKey(memberId));
    }
    return inMemoryProofByMember.get(memberId);
  }

  private String readRebindMemberIndex(Long memberId) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      return redisTemplate.opsForValue().get(rebindMemberKey(memberId));
    }
    return inMemoryRebindByMember.get(memberId);
  }

  private void clearRecoveryProofIndexIfMatches(Long memberId, String recoveryProof) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      String indexKey = recoveryProofMemberKey(memberId);
      String currentProof = redisTemplate.opsForValue().get(indexKey);
      if (recoveryProof.equals(currentProof)) {
        redisTemplate.delete(indexKey);
      }
      return;
    }

    inMemoryProofByMember.computeIfPresent(memberId, (ignored, currentProof) ->
        recoveryProof.equals(currentProof) ? null : currentProof);
  }

  private void clearRebindIndexIfMatches(Long memberId, String rebindToken) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      String indexKey = rebindMemberKey(memberId);
      String currentToken = redisTemplate.opsForValue().get(indexKey);
      if (rebindToken.equals(currentToken)) {
        redisTemplate.delete(indexKey);
      }
      return;
    }

    inMemoryRebindByMember.computeIfPresent(memberId, (ignored, currentToken) ->
        rebindToken.equals(currentToken) ? null : currentToken);
  }

  private void deleteRecoveryProof(String recoveryProof, Long memberId) {
    inMemoryProofs.remove(recoveryProof);
    if (memberId != null) {
      clearRecoveryProofIndexIfMatches(memberId, recoveryProof);
    }

    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      redisTemplate.delete(recoveryProofKey(recoveryProof));
    }
  }

  private void deleteRebindToken(String rebindToken, Long memberId) {
    inMemoryRebindTokens.remove(rebindToken);
    if (memberId != null) {
      clearRebindIndexIfMatches(memberId, rebindToken);
    }

    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      redisTemplate.delete(rebindTokenKey(rebindToken));
    }
  }

  private Duration remainingTtl(Instant expiresAt) {
    return Duration.between(Instant.now(clock), expiresAt);
  }

  private String normalize(String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return "";
    }
    return rawValue.trim();
  }

  private String recoveryProofKey(String recoveryProof) {
    return RECOVERY_PROOF_KEY_PREFIX + recoveryProof;
  }

  private String recoveryProofMemberKey(Long memberId) {
    return RECOVERY_PROOF_MEMBER_KEY_PREFIX + memberId;
  }

  private String rebindTokenKey(String rebindToken) {
    return REBIND_TOKEN_KEY_PREFIX + rebindToken;
  }

  private String rebindMemberKey(Long memberId) {
    return REBIND_TOKEN_MEMBER_KEY_PREFIX + memberId;
  }

  private BusinessException invalidRecoveryToken() {
    return new BusinessException(
        ErrorCode.AUTH_MFA_RECOVERY_TOKEN_INVALID,
        "mfa recovery proof or rebind token invalid or expired"
    );
  }

  private BusinessException consumedRecoveryToken() {
    return new BusinessException(
        ErrorCode.AUTH_MFA_RECOVERY_TOKEN_CONSUMED,
        "mfa recovery proof or rebind token already consumed"
    );
  }

  private BusinessException serializationFailure(String message, JsonProcessingException ex) {
    return new BusinessException(ErrorCode.INTERNAL_ERROR, message, ex);
  }

  public record RecoveryProof(String recoveryProof, Instant expiresAt) {
  }

  public record RecoveryProofState(String recoveryProof, Long memberId, Instant expiresAt) {
  }

  public record RebindTokenState(String rebindToken, Long memberId, Instant expiresAt) {
  }

  private record StoredRecoveryProof(
      String recoveryProof,
      Long memberId,
      Instant expiresAt,
      Instant consumedAt
  ) {
    boolean isConsumed() {
      return consumedAt != null;
    }

    boolean isExpiredAt(Instant now) {
      return expiresAt == null || !expiresAt.isAfter(now);
    }

    StoredRecoveryProof consumeAt(Instant consumedAt) {
      return new StoredRecoveryProof(recoveryProof, memberId, expiresAt, consumedAt);
    }

    RecoveryProofState toState() {
      return new RecoveryProofState(recoveryProof, memberId, expiresAt);
    }
  }

  private record StoredRebindToken(
      String rebindToken,
      Long memberId,
      Instant expiresAt,
      Instant consumedAt
  ) {
    boolean isConsumed() {
      return consumedAt != null;
    }

    boolean isExpiredAt(Instant now) {
      return expiresAt == null || !expiresAt.isAfter(now);
    }

    StoredRebindToken consumeAt(Instant consumedAt) {
      return new StoredRebindToken(rebindToken, memberId, expiresAt, consumedAt);
    }

    RebindTokenState toState() {
      return new RebindTokenState(rebindToken, memberId, expiresAt);
    }
  }
}
