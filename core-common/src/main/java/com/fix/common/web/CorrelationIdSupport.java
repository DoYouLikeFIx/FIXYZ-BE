package com.fix.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.MDC;

public final class CorrelationIdSupport {

  public static final int MAX_CORRELATION_ID_LENGTH = 64;
  public static final String REQUEST_ATTRIBUTE = CorrelationIdSupport.class.getName() + ".correlationId";

  private CorrelationIdSupport() {
  }

  public static String ensureCorrelationId(HttpServletRequest request) {
    Object attributeValue = request.getAttribute(REQUEST_ATTRIBUTE);
    if (attributeValue instanceof String existing && !existing.isBlank()) {
      return normalize(existing);
    }

    String correlationId = normalize(request.getHeader(CommonHeaders.X_CORRELATION_ID));
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }

    request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
    return correlationId;
  }

  public static String currentOrGenerate() {
    String correlationId = normalize(MDC.get("correlationId"));
    if (correlationId == null || correlationId.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return correlationId;
  }

  public static void putInMdc(String correlationId) {
    MDC.put("correlationId", normalize(correlationId));
  }

  public static void clearMdc() {
    MDC.clear();
  }

  public static String normalize(String correlationId) {
    return normalize(correlationId, MAX_CORRELATION_ID_LENGTH);
  }

  public static String normalize(String correlationId, int maxLength) {
    if (correlationId == null || correlationId.isBlank()) {
      return null;
    }
    int effectiveMaxLength = Math.max(1, maxLength);
    if (correlationId.length() <= effectiveMaxLength) {
      return correlationId;
    }
    return correlationId.substring(0, effectiveMaxLength);
  }
}
