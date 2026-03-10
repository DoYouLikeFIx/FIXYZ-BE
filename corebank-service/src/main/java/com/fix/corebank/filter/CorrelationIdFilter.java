package com.fix.corebank.filter;

import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = CorrelationIdSupport.ensureCorrelationId(request);
    response.setHeader(CommonHeaders.X_CORRELATION_ID, correlationId);
    CorrelationIdSupport.putInMdc(correlationId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      CorrelationIdSupport.clearMdc();
    }
  }
}
