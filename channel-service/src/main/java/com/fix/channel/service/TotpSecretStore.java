package com.fix.channel.service;

import com.fix.channel.entity.Member;
import java.time.Instant;
import java.util.Optional;

public interface TotpSecretStore {

  PendingTotpSecret getOrCreatePendingSecret(Member member, String loginToken, Instant expiresAt);

  Optional<PendingTotpSecret> findPendingSecret(Member member, String loginToken);

  Optional<String> findActiveSecret(Member member);

  void promotePendingSecret(Member member, String loginToken);

  void discardPendingSecret(Member member, String loginToken);

  void saveActiveSecret(Member member, String manualEntryKey);

  void terminalizeActiveSecret(Member member);

  record PendingTotpSecret(String manualEntryKey, Instant expiresAt) {
  }
}
