package com.fix.common.logging;

import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogPiiMasking {

  public static final String REDACTED = "[REDACTED]";
  private static final String SECRET_KEY_PATTERN =
      "password|currentPassword|newPassword|otp|otpCode|sessionToken|loginToken|rebindToken|"
          + "recoveryProof|challengeToken|enrollmentToken|authorization|cookie|token|email";
  private static final String ACCOUNT_KEY_PATTERN = "accountNumber|accountNo";
  private static final Set<String> ALWAYS_REDACTED_TARGET_TYPES = Set.of("SESSION");
  private static final Set<String> DIGEST_AUTH_FIELDS = Set.of(
      "username",
      "realm",
      "nonce",
      "uri",
      "response",
      "algorithm",
      "cnonce",
      "opaque",
      "qop",
      "nc",
      "userhash",
      "charset",
      "domain",
      "stale"
  );

  private static final Pattern SECRET_ASSIGNMENT_START_PATTERN = Pattern.compile(
      "(?i)\\b(" + SECRET_KEY_PATTERN + ")\\b\\s*=\\s*"
  );
  private static final Pattern SECRET_JSON_STRING_PATTERN = Pattern.compile(
      "(?i)((?:\"(?:" + SECRET_KEY_PATTERN + ")\"\\s*:\\s*\"))([^\"]*)((?:\"))"
  );
  private static final Pattern SECRET_JSON_SCALAR_PATTERN = Pattern.compile(
      "(?i)(\"(?:" + SECRET_KEY_PATTERN + ")\"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?|true|false|null)"
  );
  private static final Pattern ACCOUNT_ASSIGNMENT_PATTERN = Pattern.compile(
      "(?i)(\\b(?:" + ACCOUNT_KEY_PATTERN + ")\\b\\s*=\\s*)([^,;&\\r\\n]+)"
  );
  private static final Pattern ACCOUNT_JSON_STRING_PATTERN = Pattern.compile(
      "(?i)((?:\"(?:" + ACCOUNT_KEY_PATTERN + ")\"\\s*:\\s*\"))([^\"]*)((?:\"))"
  );
  private static final Pattern ACCOUNT_JSON_SCALAR_PATTERN = Pattern.compile(
      "(?i)(\"(?:" + ACCOUNT_KEY_PATTERN + ")\"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)"
  );
  private static final Pattern HEADER_START_PATTERN = Pattern.compile(
      "(?i)\\b(Authorization|Cookie|Set-Cookie)\\b\\s*:\\s*"
  );
  private static final Pattern COOKIE_ENTRY_PATTERN =
      Pattern.compile("(?i)\\b(JSESSIONID|SESSION)\\s*=\\s*([^;,\"\\s]+)");
  private static final Pattern GENERIC_FIELD_BOUNDARY_PATTERN =
      Pattern.compile("(?i)[A-Z][A-Z0-9_.-]*\\s*(?:=|:)", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABELED_ACCOUNT_NUMBER_PATTERN = Pattern.compile(
      "(?i)(\\b(?:account(?:\\s*(?:number|no))?|acct(?:\\s*(?:number|no))?)\\b\\s*(?:[:=]\\s*|is\\s+))"
          + "(\\d(?:[ -]?\\d){7,13})"
  );
  private static final Pattern SECRETISH_TARGET_ID_PATTERN = Pattern.compile(
      "(?i)^(?:[a-z0-9]+[-_])*(?:token|proof|session|rebind|enrollment|authorization|cookie)(?:[-_][a-z0-9]+)*$"
  );
  private static final Pattern IPV4_PATTERN =
      Pattern.compile("\\b((?:\\d{1,3}\\.){3})(\\d{1,3})\\b");
  private static final Pattern IPV6_PATTERN =
      Pattern.compile("(?i)\\b(?:[0-9a-f]{1,4}:){2,7}[0-9a-f]{1,4}\\b");

  private LogPiiMasking() {
  }

  public static String maskAccountNumber(String value) {
    String digits = digitsOnly(value);
    if (digits.isEmpty()) {
      return value;
    }
    if (digits.length() >= 8) {
      return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }
    String lastFour = digits.substring(Math.max(0, digits.length() - 4));
    return "***-" + "*".repeat(4 - lastFour.length()) + lastFour;
  }

  public static String sanitizeText(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }

    String sanitized = value;
    sanitized = redactJsonSecretValues(sanitized);
    sanitized = redactAssignmentValues(sanitized);
    sanitized = redactHeaderValues(sanitized);
    sanitized = redactCookieEntries(sanitized);
    sanitized = redactIpAddresses(sanitized);
    sanitized = maskJsonAccountNumbers(sanitized);
    sanitized = maskAssignedAccountNumbers(sanitized);
    sanitized = maskLabeledAccountNumbers(sanitized);
    return sanitized;
  }

  public static String sanitizeAuditTargetId(String targetType, String targetId) {
    if (targetId == null || targetId.isBlank()) {
      return targetId;
    }
    String normalizedTargetType = targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);
    if (ALWAYS_REDACTED_TARGET_TYPES.contains(normalizedTargetType)
        || isOpaqueSecretIdentifier(targetId)) {
      return REDACTED;
    }
    return sanitizeText(targetId);
  }

  public static String sanitizeIpAddress(String ipAddress) {
    if (ipAddress == null || ipAddress.isBlank()) {
      return ipAddress;
    }
    String trimmed = ipAddress.trim();
    if (trimmed.indexOf('.') >= 0) {
      int lastDot = trimmed.lastIndexOf('.');
      if (lastDot > 0) {
        return trimmed.substring(0, lastDot + 1) + "0";
      }
    }
    if (trimmed.indexOf(':') >= 0) {
      int lastColon = trimmed.lastIndexOf(':');
      if (lastColon > 0) {
        return trimmed.substring(0, lastColon + 1) + "0";
      }
    }
    return trimmed;
  }

  public static String sanitizeExceptionSummary(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    String message = sanitizeText(throwable.getMessage());
    String simpleName = throwable.getClass().getSimpleName();
    if (message == null || message.isBlank()) {
      return simpleName;
    }
    return simpleName + ": " + message;
  }

  private static String redactJsonSecretValues(String value) {
    String sanitized = rewriteMatches(SECRET_JSON_STRING_PATTERN, value, 1, 2, 3, ignored -> REDACTED);
    return rewriteScalarJsonMatches(SECRET_JSON_SCALAR_PATTERN, sanitized, ignored -> REDACTED);
  }

  private static String redactAssignmentValues(String value) {
    Matcher matcher = SECRET_ASSIGNMENT_START_PATTERN.matcher(value);
    StringBuilder sanitized = new StringBuilder(value.length());
    int cursor = 0;
    while (matcher.find(cursor)) {
      sanitized.append(value, cursor, matcher.start());
      String secretKey = matcher.group(1);
      int valueStart = matcher.end();
      int valueEnd = findAssignmentValueEnd(value, valueStart, secretKey);
      sanitized.append(value, matcher.start(), valueStart).append(REDACTED);
      cursor = valueEnd;
    }
    sanitized.append(value, cursor, value.length());
    return sanitized.toString();
  }

  private static String redactHeaderValues(String value) {
    Matcher matcher = HEADER_START_PATTERN.matcher(value);
    StringBuilder sanitized = new StringBuilder(value.length());
    int cursor = 0;
    while (matcher.find(cursor)) {
      sanitized.append(value, cursor, matcher.start());
      String headerName = matcher.group(1);
      int valueStart = matcher.end();
      int valueEnd = findHeaderValueEnd(value, valueStart, headerName);
      sanitized.append(value, matcher.start(), valueStart).append(REDACTED);
      cursor = valueEnd;
    }
    sanitized.append(value, cursor, value.length());
    return sanitized.toString();
  }

  private static String redactCookieEntries(String value) {
    return rewriteMatches(COOKIE_ENTRY_PATTERN, value, 1, 2, 0, ignored -> REDACTED);
  }

  private static String redactIpAddresses(String value) {
    String sanitized = rewriteMatches(IPV4_PATTERN, value, 1, 2, 0, ignored -> "0");
    Matcher matcher = IPV6_PATTERN.matcher(sanitized);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(sanitizeIpAddress(matcher.group())));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private static String maskJsonAccountNumbers(String value) {
    String sanitized = rewriteMatches(ACCOUNT_JSON_STRING_PATTERN, value, 1, 2, 3, LogPiiMasking::maskAccountNumber);
    return rewriteScalarJsonMatches(ACCOUNT_JSON_SCALAR_PATTERN, sanitized, LogPiiMasking::maskAccountNumber);
  }

  private static String maskAssignedAccountNumbers(String value) {
    return rewriteMatches(ACCOUNT_ASSIGNMENT_PATTERN, value, 1, 2, 0, LogPiiMasking::maskAccountNumber);
  }

  private static String maskLabeledAccountNumbers(String value) {
    Matcher matcher = LABELED_ACCOUNT_NUMBER_PATTERN.matcher(value);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String prefix = matcher.group(1);
      String candidate = matcher.group(2);
      String digits = digitsOnly(candidate);
      String replacement = digits.length() >= 8 && digits.length() <= 14
          ? prefix + maskAccountNumber(candidate)
          : prefix + candidate;
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private static int findAssignmentValueEnd(String value, int valueStart, String key) {
    if (key == null) {
      return findSimpleFieldEnd(value, valueStart);
    }
    String normalized = key.trim().toLowerCase(Locale.ROOT);
    if ("authorization".equals(normalized)) {
      return findAuthorizationValueEnd(value, valueStart);
    }
    if ("cookie".equals(normalized)) {
      return findCookieValueEnd(value, valueStart);
    }
    return findSimpleFieldEnd(value, valueStart);
  }

  private static int findHeaderValueEnd(String value, int valueStart, String headerName) {
    String normalized = headerName == null ? "" : headerName.trim().toLowerCase(Locale.ROOT);
    if ("authorization".equals(normalized)) {
      return findAuthorizationValueEnd(value, valueStart);
    }
    if ("cookie".equals(normalized) || "set-cookie".equals(normalized)) {
      return findCookieValueEnd(value, valueStart);
    }
    return findSimpleFieldEnd(value, valueStart);
  }

  private static int findSimpleFieldEnd(String value, int valueStart) {
    for (int index = valueStart; index < value.length(); index++) {
      char current = value.charAt(index);
      if (current == ',' || current == ';' || current == '&' || current == '\r' || current == '\n') {
        return index;
      }
    }
    return value.length();
  }

  private static int findCookieValueEnd(String value, int valueStart) {
    for (int index = valueStart; index < value.length(); index++) {
      char current = value.charAt(index);
      if (current == '&' || current == '\r' || current == '\n') {
        return index;
      }
      if (current == ',') {
        int probe = skipWhitespace(value, index + 1);
        if (probe >= value.length()) {
          return index;
        }
        if (startsWithHeader(value, probe) || startsWithGenericField(value, probe)) {
          return index;
        }
      }
    }
    return value.length();
  }

  private static int findAuthorizationValueEnd(String value, int valueStart) {
    int cursor = skipWhitespace(value, valueStart);
    int schemeEnd = cursor;
    while (schemeEnd < value.length()) {
      char current = value.charAt(schemeEnd);
      if (Character.isWhitespace(current) || current == ',' || current == ';' || current == '&'
          || current == '\r' || current == '\n') {
        break;
      }
      schemeEnd++;
    }

    String scheme = value.substring(cursor, schemeEnd);
    if ("Digest".equalsIgnoreCase(scheme)) {
      return findDigestAuthorizationEnd(value, schemeEnd);
    }
    return findSimpleFieldEnd(value, schemeEnd);
  }

  private static int findDigestAuthorizationEnd(String value, int startIndex) {
    int cursor = startIndex;
    while (cursor < value.length()) {
      char current = value.charAt(cursor);
      if (current == '&' || current == '\r' || current == '\n' || current == ';') {
        return cursor;
      }
      if (current == ',') {
        int probe = skipWhitespace(value, cursor + 1);
        if (!startsWithDigestField(value, probe)) {
          return cursor;
        }
      }
      cursor++;
    }
    return value.length();
  }

  private static int skipWhitespace(String value, int index) {
    int cursor = index;
    while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) {
      cursor++;
    }
    return cursor;
  }

  private static boolean startsWithHeader(String value, int index) {
    Matcher matcher = HEADER_START_PATTERN.matcher(value);
    return matcher.find(index) && matcher.start() == index;
  }

  private static boolean startsWithGenericField(String value, int index) {
    Matcher matcher = GENERIC_FIELD_BOUNDARY_PATTERN.matcher(value);
    return matcher.find(index) && matcher.start() == index;
  }

  private static boolean startsWithDigestField(String value, int index) {
    int separator = value.indexOf('=', index);
    if (separator < 0) {
      return false;
    }
    String fieldName = value.substring(index, separator).trim().toLowerCase(Locale.ROOT);
    return DIGEST_AUTH_FIELDS.contains(fieldName);
  }

  private static boolean isOpaqueSecretIdentifier(String targetId) {
    String normalized = targetId == null ? "" : targetId.trim().toLowerCase(Locale.ROOT);
    return SECRETISH_TARGET_ID_PATTERN.matcher(normalized).matches();
  }

  private static String rewriteScalarJsonMatches(
      Pattern pattern,
      String value,
      Function<String, String> rewriter
  ) {
    Matcher matcher = pattern.matcher(value);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String replacement = matcher.group(1) + "\"" + rewriter.apply(matcher.group(2)) + "\"";
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private static String rewriteMatches(
      Pattern pattern,
      String value,
      int prefixGroup,
      int matchedValueGroup,
      int suffixGroup,
      Function<String, String> rewriter
  ) {
    return rewriteMatches(pattern, value, prefixGroup, matchedValueGroup, suffixGroup, rewriter, "");
  }

  private static String rewriteMatches(
      Pattern pattern,
      String value,
      int prefixGroup,
      int matchedValueGroup,
      int suffixGroup,
      Function<String, String> rewriter,
      String separator
  ) {
    Matcher matcher = pattern.matcher(value);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      StringBuilder replacement = new StringBuilder(matcher.group(prefixGroup));
      if (suffixGroup == 0) {
        replacement.append(separator);
      }
      replacement.append(rewriter.apply(matcher.group(matchedValueGroup)));
      if (suffixGroup != 0) {
        replacement.append(matcher.group(suffixGroup));
      }
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private static String digitsOnly(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    StringBuilder digits = new StringBuilder(value.length());
    for (char current : value.toCharArray()) {
      if (Character.isDigit(current)) {
        digits.append(current);
      }
    }
    return digits.toString();
  }
}
