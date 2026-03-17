package com.fix.channel.support;

import com.fix.common.web.CorrelationIdSupport;
import jakarta.servlet.http.HttpServletRequest;

public final class ChannelCorrelationIdSupport {

  public static final int MAX_CHANNEL_CORRELATION_ID_LENGTH = 36;

  private ChannelCorrelationIdSupport() {
  }

  public static String ensureCorrelationId(HttpServletRequest request) {
    String correlationId = normalize(CorrelationIdSupport.ensureCorrelationId(request));
    request.setAttribute(CorrelationIdSupport.REQUEST_ATTRIBUTE, correlationId);
    return correlationId;
  }

  public static String currentOrGenerate() {
    return normalize(CorrelationIdSupport.currentOrGenerate());
  }

  public static String normalize(String correlationId) {
    return CorrelationIdSupport.normalize(correlationId, MAX_CHANNEL_CORRELATION_ID_LENGTH);
  }
}
