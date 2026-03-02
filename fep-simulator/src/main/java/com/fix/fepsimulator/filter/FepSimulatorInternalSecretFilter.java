package com.fix.fepsimulator.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FepSimulatorInternalSecretFilter extends OncePerRequestFilter {

  private final String expectedSecret;

  public FepSimulatorInternalSecretFilter(@Value("${internal.secret:local-internal-secret}") String expectedSecret) {
    this.expectedSecret = expectedSecret;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getRequestURI().startsWith("/fep-internal/")) {
      String provided = request.getHeader("X-Internal-Secret");
      if (provided == null || !provided.equals(expectedSecret)) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid X-Internal-Secret");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }
}
