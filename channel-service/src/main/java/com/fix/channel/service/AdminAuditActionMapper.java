package com.fix.channel.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AdminAuditActionMapper {

  private static final Map<String, Set<String>> CANONICAL_TO_STORED = Map.ofEntries(
      Map.entry("LOGIN_SUCCESS", Set.of("AUTH_LOGIN_SUCCESS")),
      Map.entry("LOGIN_FAIL", Set.of("AUTH_LOGIN_FAILURE")),
      Map.entry("LOGOUT", Set.of("LOGOUT")),
      Map.entry("ADMIN_FORCE_LOGOUT", Set.of("ADMIN_FORCE_LOGOUT")),
      Map.entry("ORDER_SESSION_CREATE", Set.of("ORDER_SESSION_CREATE")),
      Map.entry("ORDER_OTP_SUCCESS", Set.of("ORDER_SESSION_OTP_VERIFIED")),
      Map.entry(
          "ORDER_OTP_FAIL",
          Set.of("ORDER_SESSION_OTP_FAILED", "ORDER_SESSION_OTP_RATE_LIMITED", "ORDER_SESSION_OTP_REPLAYED")
      ),
      Map.entry("ORDER_EXECUTE", Set.of("ORDER_SESSION_EXECUTED")),
      Map.entry("ORDER_CANCEL", Set.of("ORDER_SESSION_CANCELED")),
      Map.entry("ORDER_RECOVERY", Set.of("ORDER_SESSION_RECOVERY_ATTEMPT")),
      Map.entry("ORDER_RECONCILIATION", Set.of("ORDER_SESSION_RECONCILIATION")),
      Map.entry("MANUAL_REPLAY", Set.of("MANUAL_REPLAY")),
      Map.entry("TOTP_ENROLL", Set.of("AUTH_TOTP_ENROLLMENT_BOOTSTRAP")),
      Map.entry("TOTP_CONFIRM", Set.of("AUTH_TOTP_ENROLLMENT_CONFIRMED"))
  );

  private static final Map<String, String> STORED_TO_CANONICAL = CANONICAL_TO_STORED.entrySet().stream()
      .flatMap(entry -> entry.getValue().stream().map(value -> Map.entry(value, entry.getKey())))
      .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left));

  public String canonicalize(String storedAction) {
    if (storedAction == null || storedAction.isBlank()) {
      return null;
    }
    return STORED_TO_CANONICAL.getOrDefault(storedAction.trim(), storedAction.trim());
  }

  public List<String> storedActionsForCanonical(String canonicalAction) {
    if (canonicalAction == null || canonicalAction.isBlank()) {
      return List.of();
    }
    return CANONICAL_TO_STORED.getOrDefault(normalize(canonicalAction), Set.of()).stream()
        .sorted()
        .toList();
  }

  public Set<String> supportedCanonicalActions() {
    return CANONICAL_TO_STORED.keySet();
  }

  private String normalize(String action) {
    return action.trim().toUpperCase(Locale.ROOT);
  }
}
