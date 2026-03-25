package com.fix.corebank.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.ErrorCode;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.common.web.TraceparentSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InternalSecretFilter extends OncePerRequestFilter {

  // TODO: extract to core-common when adding a 4th service
  private final String expectedSecret;
  private final ObjectMapper objectMapper;

  public InternalSecretFilter(
      @Value("${internal.secret}") String expectedSecret,
      ObjectMapper objectMapper
  ) {
    this.expectedSecret = expectedSecret;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (requiresInternalSecret(request.getRequestURI())) {
      String provided = request.getHeader(CommonHeaders.X_INTERNAL_SECRET);
      if (provided == null || !provided.equals(expectedSecret)) {
        writeUnauthorizedResponse(request, response);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private boolean requiresInternalSecret(String requestUri) {
    return CorebankInternalSecretPaths.requiresInternalSecret(requestUri);
  }

  private void writeUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String correlationId = CorrelationIdSupport.ensureCorrelationId(request);
    String traceparent = TraceparentSupport.ensureTraceparent(request);

    ApiErrorResponse body = ApiErrorResponse.from(
        ErrorCode.AUTH_REQUIRED,
        "Missing or invalid " + CommonHeaders.X_INTERNAL_SECRET,
        request.getRequestURI(),
        correlationId
    );

    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader(CommonHeaders.X_CORRELATION_ID, correlationId);
    response.setHeader(CommonHeaders.TRACEPARENT, traceparent);
    objectMapper.writeValue(response.getWriter(), body);
  }
}
