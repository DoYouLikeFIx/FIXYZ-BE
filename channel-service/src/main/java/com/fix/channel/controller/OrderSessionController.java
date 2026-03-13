package com.fix.channel.controller;

import com.fix.channel.dto.request.OrderSessionCreateRequest;
import com.fix.channel.dto.request.OrderSessionOtpVerifyRequest;
import com.fix.channel.dto.response.OrderSessionResponse;
import com.fix.channel.service.OrderExecutionService;
import com.fix.channel.service.OrderSessionService;
import com.fix.channel.vo.OrderSessionQueryCommand;
import com.fix.common.error.ApiResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.validation.ContractPatterns;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/orders/sessions")
public class OrderSessionController {

  private static final String AUTH_MEMBER_ID = "AUTH_MEMBER_ID";
  private static final String AUTH_LAST_MFA_VERIFIED_AT = "AUTH_LAST_MFA_VERIFIED_AT";
  private static final String AUTH_LOGIN_CLIENT_IP = "AUTH_LOGIN_CLIENT_IP";
  private static final String AUTH_LOGIN_USER_AGENT = "AUTH_LOGIN_USER_AGENT";

  private final OrderSessionService orderSessionService;
  private final OrderExecutionService orderExecutionService;

  public OrderSessionController(
      OrderSessionService orderSessionService,
      OrderExecutionService orderExecutionService
  ) {
    this.orderSessionService = orderSessionService;
    this.orderExecutionService = orderExecutionService;
  }

  @Operation(summary = "Create or replay an order session")
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Existing active session replayed")
  })
  @PostMapping
  public ResponseEntity<ApiResponse<OrderSessionResponse>> create(
      @Parameter(description = "Client UUID v4 (FIX Tag 11)")
      @RequestHeader(value = "X-ClOrdID", required = false)
      @NotBlank(message = "X-ClOrdID header is required")
      @Pattern(regexp = ContractPatterns.UUID_V4, message = "X-ClOrdID must be UUID v4")
      String clOrdId,
      @Valid @RequestBody OrderSessionCreateRequest request,
      HttpServletRequest httpServletRequest
  ) {
    var result = orderSessionService.createOrderSession(
        request.toVo(
            resolveAuthenticatedMemberId(httpServletRequest),
            clOrdId,
            resolveLastMfaVerifiedAt(httpServletRequest),
            resolveSessionStringAttribute(httpServletRequest, AUTH_LOGIN_CLIENT_IP),
            resolveSessionStringAttribute(httpServletRequest, AUTH_LOGIN_USER_AGENT),
            resolveClientIp(httpServletRequest),
            resolveUserAgent(httpServletRequest)
        )
    );
    HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ApiResponse.success(OrderSessionResponse.from(result)));
  }

  @Operation(summary = "Get an order session")
  @GetMapping("/{orderSessionId}")
  public ApiResponse<OrderSessionResponse> get(
      @PathVariable
      @NotBlank(message = "orderSessionId is required")
      @Pattern(regexp = ContractPatterns.UUID_V4, message = "orderSessionId must be UUID v4")
      String orderSessionId,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(OrderSessionResponse.from(
        orderSessionService.getOrderSession(OrderSessionQueryCommand.of(
            resolveAuthenticatedMemberId(httpServletRequest),
            orderSessionId
        ))
    ));
  }

  @Operation(summary = "Verify step-up OTP for an order session")
  @PostMapping("/{orderSessionId}/otp/verify")
  public ApiResponse<OrderSessionResponse> verifyOtp(
      @PathVariable
      @NotBlank(message = "orderSessionId is required")
      @Pattern(regexp = ContractPatterns.UUID_V4, message = "orderSessionId must be UUID v4")
      String orderSessionId,
      @Valid @RequestBody OrderSessionOtpVerifyRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(OrderSessionResponse.from(
        orderSessionService.verifyOtp(
            request.toVo(resolveAuthenticatedMemberId(httpServletRequest), orderSessionId)
        )
    ));
  }

  @Operation(summary = "Execute an authorized order session")
  @PostMapping("/{orderSessionId}/execute")
  public ApiResponse<OrderSessionResponse> execute(
      @PathVariable
      @NotBlank(message = "orderSessionId is required")
      @Pattern(regexp = ContractPatterns.UUID_V4, message = "orderSessionId must be UUID v4")
      String orderSessionId,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(OrderSessionResponse.from(
        orderExecutionService.execute(resolveAuthenticatedMemberId(httpServletRequest), orderSessionId)
    ));
  }

  private Long resolveAuthenticatedMemberId(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }

    Object memberIdAttr = session.getAttribute(AUTH_MEMBER_ID);
    if (!(memberIdAttr instanceof Number memberIdNumber)) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }
    return memberIdNumber.longValue();
  }

  private Instant resolveLastMfaVerifiedAt(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return null;
    }

    Object value = session.getAttribute(AUTH_LAST_MFA_VERIFIED_AT);
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof String instantString && !instantString.isBlank()) {
      try {
        return Instant.parse(instantString);
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
    return null;
  }

  private String resolveSessionStringAttribute(HttpServletRequest request, String attributeName) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return null;
    }
    Object value = session.getAttribute(attributeName);
    if (value instanceof String stringValue && !stringValue.isBlank()) {
      return stringValue;
    }
    return null;
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
}
