package com.fix.channel.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fix.channel.dto.request.AdminAccountStatusTransitionRequest;
import com.fix.channel.dto.request.AdminAuditLogQueryRequest;
import com.fix.channel.dto.request.AdminOrderReplayRequest;
import com.fix.channel.dto.request.AdminSecurityEventRequest;
import com.fix.channel.dto.response.AdminAccountStatusTransitionResponse;
import com.fix.channel.dto.response.AdminAuditLogQueryResponse;
import com.fix.channel.dto.response.AdminOrderReplayResponse;
import com.fix.channel.dto.response.AdminSecurityEventResponse;
import com.fix.channel.dto.response.AdminSessionInvalidationResponse;
import com.fix.channel.service.AdminAccountStatusService;
import com.fix.channel.service.AdminApiRateLimitService;
import com.fix.channel.service.AdminAuditLogQueryService;
import com.fix.channel.service.AdminMemberSessionService;
import com.fix.channel.service.AdminOrderReplayService;
import com.fix.channel.service.ChannelScaffoldService;
import com.fix.channel.support.ChannelCorrelationIdSupport;
import com.fix.channel.vo.AdminActorContext;
import com.fix.common.error.ApiResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

  private final ChannelScaffoldService channelScaffoldService;
  private final AdminAccountStatusService adminAccountStatusService;
  private final AdminAuditLogQueryService adminAuditLogQueryService;
  private final AdminMemberSessionService adminMemberSessionService;
  private final AdminOrderReplayService adminOrderReplayService;
  private final AdminApiRateLimitService adminApiRateLimitService;

  public AdminController(
      ChannelScaffoldService channelScaffoldService,
      AdminAccountStatusService adminAccountStatusService,
      AdminAuditLogQueryService adminAuditLogQueryService,
      AdminMemberSessionService adminMemberSessionService,
      AdminOrderReplayService adminOrderReplayService,
      AdminApiRateLimitService adminApiRateLimitService
  ) {
    this.channelScaffoldService = channelScaffoldService;
    this.adminAccountStatusService = adminAccountStatusService;
    this.adminAuditLogQueryService = adminAuditLogQueryService;
    this.adminMemberSessionService = adminMemberSessionService;
    this.adminOrderReplayService = adminOrderReplayService;
    this.adminApiRateLimitService = adminApiRateLimitService;
  }

  @GetMapping("/security-events")
  public ApiResponse<AdminSecurityEventResponse> securityEvents(
      @Valid @ModelAttribute AdminSecurityEventRequest request
  ) {
    return ApiResponse.success(AdminSecurityEventResponse.from(channelScaffoldService.getSecurityEvents(request.toVo())));
  }

  @GetMapping("/audit-logs")
    @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
        content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
        content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too Many Requests",
        headers = @Header(name = "Retry-After", description = "Seconds until the rate-limit window resets",
          schema = @Schema(type = "string")),
        content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class)))
    })
  public ApiResponse<AdminAuditLogQueryResponse> auditLogs(
      @RequestParam(required = false) @Min(0) Integer page,
      @RequestParam(required = false) @Min(1) @Max(100) Integer size,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) Long memberId,
      @RequestParam(required = false) String eventType,
      HttpServletRequest httpServletRequest
  ) {
    enforceAdminRateLimit(httpServletRequest);
    AdminAuditLogQueryRequest request = new AdminAuditLogQueryRequest(page, size, from, to, memberId, eventType);
    return ApiResponse.success(AdminAuditLogQueryResponse.from(
        adminAuditLogQueryService.query(request.toVo())
    ));
  }

  @PostMapping(value = "/orders/{clOrdId}/replay", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized",
          content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden",
          content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
          content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflict",
          content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation failed",
          content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too Many Requests",
          headers = @Header(name = "Retry-After", description = "Seconds until the rate-limit window resets",
              schema = @Schema(type = "string")),
          content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Corebank unavailable",
          content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class)))
  })
  public ApiResponse<AdminOrderReplayResponse> replayOrder(
      @PathVariable String clOrdId,
      @RequestBody AdminOrderReplayRequest request,
      HttpServletRequest httpServletRequest
  ) {
    AdminActorContext actor = resolveAdminActorContext(httpServletRequest);
    adminApiRateLimitService.enforceOrderReplay(actor.getSessionId());
    return ApiResponse.success(AdminOrderReplayResponse.from(
        adminOrderReplayService.replay(clOrdId, request.toVo(), actor)
    ));
  }

  @DeleteMapping("/members/{memberUuid}/sessions")
    @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
        content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too Many Requests",
        headers = @Header(name = "Retry-After", description = "Seconds until the rate-limit window resets",
          schema = @Schema(type = "string")),
        content = @Content(schema = @Schema(implementation = com.fix.common.error.ApiErrorResponse.class)))
    })
  public ApiResponse<AdminSessionInvalidationResponse> invalidateMemberSessions(
      @PathVariable String memberUuid,
      HttpServletRequest httpServletRequest
  ) {
    AdminActorContext actor = resolveAdminActorContext(httpServletRequest);
    adminApiRateLimitService.enforceSessionInvalidation(actor.getSessionId());
    return ApiResponse.success(AdminSessionInvalidationResponse.from(
        adminMemberSessionService.invalidateMemberSessions(memberUuid, actor)
    ));
  }

  @PatchMapping("/accounts/{accountId}/status")
  public ApiResponse<AdminAccountStatusTransitionResponse> transitionAccountStatus(
      @PathVariable Long accountId,
      @Valid @RequestBody AdminAccountStatusTransitionRequest request
  ) {
    return ApiResponse.success(AdminAccountStatusTransitionResponse.from(
        adminAccountStatusService.transitionAccountStatus(request.toVo(accountId))
    ));
  }

  private void enforceAdminRateLimit(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }
    adminApiRateLimitService.enforceAuditLogs(session.getId());
  }

  private AdminActorContext resolveAdminActorContext(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }

    Object memberIdAttr = session.getAttribute("AUTH_MEMBER_ID");
    if (!(memberIdAttr instanceof Number memberIdNumber)) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }
    String adminEmail = authentication.getName().trim();

    String operatorId = adminMemberSessionService.resolveOperatorId(memberIdNumber.longValue());

    Object principalName = session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
    if (!(principalName instanceof String principalEmail)
        || principalEmail.isBlank()
        || !adminEmail.equals(principalEmail)) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication context mismatch");
    }

    return AdminActorContext.of(
        memberIdNumber.longValue(),
        operatorId,
        adminEmail,
        session.getId(),
        resolveClientIp(request),
        request.getHeader("User-Agent"),
        ChannelCorrelationIdSupport.ensureCorrelationId(request)
    );
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
