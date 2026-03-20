package com.fix.common.openapi;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class OpenApiSummarySupport {

  private static final Set<String> UPPERCASE_TOKENS = Set.of(
      "api",
      "csrf",
      "fep",
      "id",
      "mfa",
      "otp",
      "pii",
      "sse",
      "totp",
      "uuid"
  );

  private OpenApiSummarySupport() {
  }

  public static String fromMethodName(String methodName) {
    if (methodName == null || methodName.isBlank()) {
      return "";
    }

    String normalized = methodName.trim().replaceAll("_\\d+$", "");
    String spaced = normalized.replaceAll("([a-z0-9])([A-Z])", "$1 $2");

    return Arrays.stream(spaced.split("\\s+"))
        .filter(token -> !token.isBlank())
        .map(OpenApiSummarySupport::formatToken)
        .collect(Collectors.joining(" "));
  }

  private static String formatToken(String token) {
    String lower = token.toLowerCase(Locale.ROOT);
    if (UPPERCASE_TOKENS.contains(lower)) {
      return lower.toUpperCase(Locale.ROOT);
    }
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }
}
