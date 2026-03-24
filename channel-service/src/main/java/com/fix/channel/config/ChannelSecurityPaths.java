package com.fix.channel.config;

import java.util.List;
import org.springframework.util.AntPathMatcher;

public final class ChannelSecurityPaths {

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

  private static final List<String> PUBLIC_PATH_PATTERNS = List.of(
      "/api/v1/ping",
      "/api/v1/errors/boom",
      "/api/v1/auth/csrf",
      "/api/v1/auth/register",
      "/api/v1/auth/login",
      "/api/v1/auth/otp/verify",
      "/api/v1/auth/mfa-recovery/rebind",
      "/api/v1/auth/mfa-recovery/rebind/confirm",
      "/api/v1/members/me/totp/enroll",
      "/api/v1/members/me/totp/confirm",
      "/api/v1/auth/password/forgot",
      "/api/v1/auth/password/forgot/challenge",
      "/api/v1/auth/password/forgot/challenge/fail-closed",
      "/api/v1/auth/password/reset",
      "/swagger-ui/**",
      "/v3/api-docs/**",
      "/actuator/health",
      "/actuator/info",
      "/actuator/prometheus"
  );

  private static final List<String> ADMIN_ONLY_PATH_PATTERNS = List.of(
      "/api/v1/admin/**",
      "/actuator/**"
  );

  private ChannelSecurityPaths() {
  }

  public static String[] publicPathPatterns() {
    return PUBLIC_PATH_PATTERNS.toArray(String[]::new);
  }

  public static String[] adminOnlyPathPatterns() {
    return ADMIN_ONLY_PATH_PATTERNS.toArray(String[]::new);
  }

  public static boolean isPublicPath(String path) {
    return matchesAny(path, PUBLIC_PATH_PATTERNS);
  }

  public static boolean requiresAdminRole(String path) {
    return matchesAny(path, ADMIN_ONLY_PATH_PATTERNS) && !isPublicPath(path);
  }

  public static boolean requiresAuthentication(String path) {
    return !isPublicPath(path);
  }

  private static boolean matchesAny(String path, List<String> patterns) {
    return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
  }
}
