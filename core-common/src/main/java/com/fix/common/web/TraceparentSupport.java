package com.fix.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.MDC;

public final class TraceparentSupport {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final HexFormat HEX_FORMAT = HexFormat.of();
  private static final Pattern TRACEPARENT_PATTERN =
      Pattern.compile("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");

  public static final String MDC_KEY = "traceparent";
  public static final String REQUEST_ATTRIBUTE = TraceparentSupport.class.getName() + ".traceparent";

  private TraceparentSupport() {
  }

  public static String ensureTraceparent(HttpServletRequest request) {
    Object attributeValue = request.getAttribute(REQUEST_ATTRIBUTE);
    if (attributeValue instanceof String existing && !existing.isBlank()) {
      return existing;
    }

    String traceparent = normalize(request.getHeader(CommonHeaders.TRACEPARENT));
    if (traceparent == null) {
      traceparent = generate();
    }

    request.setAttribute(REQUEST_ATTRIBUTE, traceparent);
    return traceparent;
  }

  public static String currentOrGenerate() {
    String traceparent = normalize(MDC.get(MDC_KEY));
    if (traceparent == null) {
      return generate();
    }
    return traceparent;
  }

  public static void putInMdc(String traceparent) {
    String normalized = normalize(traceparent);
    if (normalized != null) {
      MDC.put(MDC_KEY, normalized);
    }
  }

  public static String normalize(String traceparent) {
    if (traceparent == null || traceparent.isBlank()) {
      return null;
    }

    String normalized = traceparent.trim().toLowerCase(Locale.ROOT);
    if (!TRACEPARENT_PATTERN.matcher(normalized).matches()) {
      return null;
    }

    String traceId = normalized.substring(3, 35);
    String parentId = normalized.substring(36, 52);
    if (isAllZeros(traceId) || isAllZeros(parentId)) {
      return null;
    }
    return normalized;
  }

  private static String generate() {
    byte[] traceId = new byte[16];
    byte[] parentId = new byte[8];

    fillNonZero(traceId);
    fillNonZero(parentId);

    return "00-%s-%s-01".formatted(
        HEX_FORMAT.formatHex(traceId),
        HEX_FORMAT.formatHex(parentId)
    );
  }

  private static void fillNonZero(byte[] bytes) {
    do {
      SECURE_RANDOM.nextBytes(bytes);
    } while (isAllZeros(HEX_FORMAT.formatHex(bytes)));
  }

  private static boolean isAllZeros(String value) {
    for (int index = 0; index < value.length(); index++) {
      if (value.charAt(index) != '0') {
        return false;
      }
    }
    return true;
  }
}
