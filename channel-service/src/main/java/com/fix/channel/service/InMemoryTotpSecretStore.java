package com.fix.channel.service;

import com.fix.channel.entity.Member;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "auth.totp.secret-store", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryTotpSecretStore implements TotpSecretStore {

  private final Clock clock;
  private final ConcurrentMap<Long, String> activeSecrets = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, PendingTotpSecret> pendingSecrets = new ConcurrentHashMap<>();

  public InMemoryTotpSecretStore(Clock clock) {
    this.clock = clock;
  }

  @Override
  public PendingTotpSecret getOrCreatePendingSecret(Member member, String loginToken, Instant expiresAt) {
    String key = pendingKey(member, loginToken);
    PendingTotpSecret pendingSecret = pendingSecrets.compute(key, (ignored, existing) -> {
      Instant now = Instant.now(clock);
      if (existing != null && existing.expiresAt() != null && existing.expiresAt().isAfter(now)) {
        return existing;
      }
      return new PendingTotpSecret(TotpService.generateRandomManualEntryKey(), expiresAt);
    });
    return pendingSecret;
  }

  @Override
  public Optional<PendingTotpSecret> findPendingSecret(Member member, String loginToken) {
    PendingTotpSecret pendingSecret = pendingSecrets.get(pendingKey(member, loginToken));
    if (pendingSecret == null) {
      return Optional.empty();
    }
    if (pendingSecret.expiresAt() != null && !pendingSecret.expiresAt().isAfter(Instant.now(clock))) {
      pendingSecrets.remove(pendingKey(member, loginToken));
      return Optional.empty();
    }
    return Optional.of(pendingSecret);
  }

  @Override
  public Optional<String> findActiveSecret(Member member) {
    return Optional.ofNullable(activeSecrets.get(member.getId()));
  }

  @Override
  public void promotePendingSecret(Member member, String loginToken) {
    PendingTotpSecret pendingSecret = findPendingSecret(member, loginToken).orElseThrow();
    activeSecrets.put(member.getId(), pendingSecret.manualEntryKey());
    pendingSecrets.remove(pendingKey(member, loginToken));
  }

  @Override
  public void saveActiveSecret(Member member, String manualEntryKey) {
    activeSecrets.put(member.getId(), manualEntryKey);
  }

  private String pendingKey(Member member, String loginToken) {
    return member.getId() + ":" + loginToken.trim();
  }
}
