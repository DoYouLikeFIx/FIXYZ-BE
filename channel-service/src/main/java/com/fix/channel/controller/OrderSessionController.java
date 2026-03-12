package com.fix.channel.controller;

import com.fix.channel.dto.request.OrderSessionCreateRequest;
import com.fix.channel.dto.request.OrderSessionQueryRequest;
import com.fix.channel.dto.response.OrderSessionResponse;
import com.fix.channel.session.ChannelSessionAttributes;
import com.fix.channel.session.ChannelSessionRequestLock;
import com.fix.channel.service.OrderSessionService;
import com.fix.common.error.ApiResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/sessions")
public class OrderSessionController {

  private final OrderSessionService orderSessionService;
  private final ChannelSessionRequestLock channelSessionRequestLock;

  public OrderSessionController(
      OrderSessionService orderSessionService,
      ChannelSessionRequestLock channelSessionRequestLock
  ) {
    this.orderSessionService = orderSessionService;
    this.channelSessionRequestLock = channelSessionRequestLock;
  }

  @Operation(summary = "Create or replay an order session")
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Existing active session replayed")
  })
  @PostMapping
  public ResponseEntity<ApiResponse<OrderSessionResponse>> create(
      @Valid @RequestBody OrderSessionCreateRequest request,
      HttpServletRequest httpServletRequest
  ) {
    HttpSession session = requireAuthenticatedSession(httpServletRequest);
    return channelSessionRequestLock.executeLocked(session.getId(), () -> {
      var result = orderSessionService.createOrderSession(request.toVo(
          resolveAuthenticatedMemberId(session),
          resolveLastMfaVerifiedAt(session),
          resolveLoginAuthenticatedAt(session),
          resolveChallengeBypassEligible(session),
          resolveLoginIpAddress(session),
          resolveLoginUserAgent(session),
          resolveClientIp(httpServletRequest),
          resolveUserAgent(httpServletRequest)
      ));
      consumeFreshLoginBypassIfUsed(session, result);
      HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
      return ResponseEntity.status(status).body(ApiResponse.success(OrderSessionResponse.from(result)));
    });
  }

  @Operation(
      summary = "Get an order session",
      description = "Provide exactly one of orderSessionId or clOrdId. Do not send both."
  )
  @GetMapping
  public ApiResponse<OrderSessionResponse> get(
      @Validated @ParameterObject @ModelAttribute OrderSessionQueryRequest request,
      HttpServletRequest httpServletRequest
  ) {
    HttpSession session = requireAuthenticatedSession(httpServletRequest);
    return ApiResponse.success(OrderSessionResponse.from(
        orderSessionService.getOrderSession(request.toVo(resolveAuthenticatedMemberId(session)))
    ));
  }

  private HttpSession requireAuthenticatedSession(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }
    resolveAuthenticatedMemberId(session);
    return session;
  }

  private Long resolveAuthenticatedMemberId(HttpSession session) {
    Object memberIdAttr = session.getAttribute(ChannelSessionAttributes.AUTH_MEMBER_ID);
    if (!(memberIdAttr instanceof Number memberIdNumber)) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }
    return memberIdNumber.longValue();
  }

  private Instant resolveLastMfaVerifiedAt(HttpSession session) {
    Object value = session.getAttribute(ChannelSessionAttributes.AUTH_MFA_VERIFIED_AT);
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof Number epochMillis) {
      return Instant.ofEpochMilli(epochMillis.longValue());
    }
    if (value instanceof String timestamp && !timestamp.isBlank()) {
      try {
        return Instant.parse(timestamp);
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
    return null;
  }

  private boolean resolveChallengeBypassEligible(HttpSession session) {
    Object value = session.getAttribute(ChannelSessionAttributes.AUTH_ORDER_CHALLENGE_BYPASS_ELIGIBLE);
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    if (value instanceof String stringValue) {
      return Boolean.parseBoolean(stringValue);
    }
    return false;
  }

  private Instant resolveLoginAuthenticatedAt(HttpSession session) {
    Object value = session.getAttribute(ChannelSessionAttributes.AUTH_LOGIN_AUTHENTICATED_AT);
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof Number epochMillis) {
      return Instant.ofEpochMilli(epochMillis.longValue());
    }
    if (value instanceof String timestamp && !timestamp.isBlank()) {
      try {
        return Instant.parse(timestamp);
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
    return null;
  }

  private String resolveLoginIpAddress(HttpSession session) {
    Object value = session.getAttribute(ChannelSessionAttributes.AUTH_LOGIN_IP_ADDRESS);
    return value instanceof String ipAddress && !ipAddress.isBlank() ? ipAddress : null;
  }

  private String resolveLoginUserAgent(HttpSession session) {
    Object value = session.getAttribute(ChannelSessionAttributes.AUTH_LOGIN_USER_AGENT);
    return value instanceof String userAgent && !userAgent.isBlank() ? userAgent : null;
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String resolveUserAgent(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    if (userAgent == null || userAgent.isBlank()) {
      return "unknown";
    }
    return userAgent;
  }

  private void consumeFreshLoginBypassIfUsed(HttpSession session, com.fix.channel.vo.OrderSessionResult result) {
    if (result.isCreated()
        && !result.isChallengeRequired()
        && "LOGIN_MFA_FRESH".equals(result.getAuthorizationReason())) {
      session.setAttribute(ChannelSessionAttributes.AUTH_ORDER_CHALLENGE_BYPASS_ELIGIBLE, false);
    }
  }
}
