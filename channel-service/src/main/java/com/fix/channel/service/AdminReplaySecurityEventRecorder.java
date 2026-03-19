package com.fix.channel.service;

import com.fix.channel.entity.SecurityEvent;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.common.web.CorrelationIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminReplaySecurityEventRecorder {

  private static final Pattern REPLAY_PATH =
      Pattern.compile("^/api/v1/admin/orders/([^/]+)/replay$");

  private final SecurityEventService securityEventService;
  private final OrderSessionRepository orderSessionRepository;

  public void recordIfApplicable(HttpServletRequest request, String correlationId) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return;
    }

    Matcher matcher = REPLAY_PATH.matcher(request.getRequestURI());
    if (!matcher.matches()) {
      return;
    }

    String clOrdId = matcher.group(1);
    Long memberId = resolveMemberId(request.getSession(false));
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String principal = authentication == null ? null : authentication.getName();

    SecurityEvent securityEvent = SecurityEvent.of(
        memberId,
        "MANUAL_REPLAY_FORBIDDEN",
        resolveClientIp(request),
        request.getHeader("User-Agent"),
        "HIGH"
    ).withCorrelationId(CorrelationIdSupport.normalize(correlationId, 36))
        .withDetail(
            "path=" + request.getRequestURI()
                + ",clOrdId=" + clOrdId
                + ",principal=" + (principal == null ? "" : principal)
                + ",reason=AUTH-006"
        );

    orderSessionRepository.findByClOrdId(clOrdId)
        .ifPresent(session -> securityEvent.withOrderSessionId(session.getId()));

    securityEventService.record(securityEvent);
  }

  private Long resolveMemberId(HttpSession session) {
    if (session == null) {
      return null;
    }
    Object memberId = session.getAttribute("AUTH_MEMBER_ID");
    return memberId instanceof Number number ? number.longValue() : null;
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      String first = forwardedFor.split(",", 2)[0].trim();
      if (!first.isBlank()) {
        return first;
      }
    }
    String realIp = request.getHeader("X-Real-IP");
    if (realIp != null && !realIp.isBlank()) {
      return realIp.trim();
    }
    return request.getRemoteAddr();
  }
}
