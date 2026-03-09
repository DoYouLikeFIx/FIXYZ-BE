package com.fix.fepgateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.ErrorCode;
import com.fix.common.web.CommonHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FepGatewayInternalSecretFilter extends OncePerRequestFilter {

  private final String expectedSecret;
  private final ObjectMapper objectMapper;

  public FepGatewayInternalSecretFilter(
      @Value("${internal.secret:local-internal-secret}") String expectedSecret,
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
    return requestUri.startsWith("/fep/") || requestUri.startsWith("/fep-internal/");
  }

  private void writeUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String correlationId = request.getHeader(CommonHeaders.X_CORRELATION_ID);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }

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
    objectMapper.writeValue(response.getWriter(), body);
  }
}
