package com.fix.fepgateway.filter;

import com.fix.common.web.CommonHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FepGatewayInternalSecretFilter extends OncePerRequestFilter {

  private final String expectedSecret;

  public FepGatewayInternalSecretFilter(@Value("${internal.secret:local-internal-secret}") String expectedSecret) {
    this.expectedSecret = expectedSecret;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getRequestURI().startsWith("/fep-internal/")) {
      String provided = request.getHeader(CommonHeaders.X_INTERNAL_SECRET);
      if (provided == null || !provided.equals(expectedSecret)) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid " + CommonHeaders.X_INTERNAL_SECRET);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }
}
