package com.fix.channel.controller;

import com.fix.channel.dto.request.AdminAccountStatusTransitionRequest;
import com.fix.channel.dto.request.AdminAuditLogQueryRequest;
import com.fix.channel.dto.request.AdminSecurityEventRequest;
import com.fix.channel.dto.response.AdminAccountStatusTransitionResponse;
import com.fix.channel.dto.response.AdminAuditLogQueryResponse;
import com.fix.channel.dto.response.AdminSecurityEventResponse;
import com.fix.channel.dto.response.AdminSessionInvalidationResponse;
import com.fix.channel.service.AdminAccountStatusService;
import com.fix.channel.service.AdminApiRateLimitService;
import com.fix.channel.service.AdminAuditLogQueryService;
import com.fix.channel.service.AdminMemberSessionService;
import com.fix.channel.service.ChannelScaffoldService;
import com.fix.channel.support.ChannelCorrelationIdSupport;
import com.fix.channel.vo.AdminActorContext;
import com.fix.common.error.ApiResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

  private final ChannelScaffoldService channelScaffoldService;
  private final AdminAccountStatusService adminAccountStatusService;
  private final AdminAuditLogQueryService adminAuditLogQueryService;
  private final AdminMemberSessionService adminMemberSessionService;
  private final AdminApiRateLimitService adminApiRateLimitService;

  public AdminController(
      ChannelScaffoldService channelScaffoldService,
      AdminAccountStatusService adminAccountStatusService,
      AdminAuditLogQueryService adminAuditLogQueryService,
      AdminMemberSessionService adminMemberSessionService,
      AdminApiRateLimitService adminApiRateLimitService
  ) {
    this.channelScaffoldService = channelScaffoldService;
    this.adminAccountStatusService = adminAccountStatusService;
    this.adminAuditLogQueryService = adminAuditLogQueryService;
    this.adminMemberSessionService = adminMemberSessionService;
    this.adminApiRateLimitService = adminApiRateLimitService;
  }

  @GetMapping("/security-events")
  public ApiResponse<AdminSecurityEventResponse> securityEvents(
      @Valid @ModelAttribute AdminSecurityEventRequest request
  ) {
    return ApiResponse.success(AdminSecurityEventResponse.from(channelScaffoldService.getSecurityEvents(request.toVo())));
  }

  @GetMapping("/audit-logs")
  public ApiResponse<AdminAuditLogQueryResponse> auditLogs(
      @Valid @ModelAttribute AdminAuditLogQueryRequest request,
      HttpServletRequest httpServletRequest
  ) {
    enforceAdminRateLimit(httpServletRequest);
    return ApiResponse.success(AdminAuditLogQueryResponse.from(
        adminAuditLogQueryService.query(request.toVo())
    ));
  }

  @DeleteMapping("/members/{memberUuid}/sessions")
  public ApiResponse<AdminSessionInvalidationResponse> invalidateMemberSessions(
      @PathVariable String memberUuid,
      HttpServletRequest httpServletRequest
  ) {
    AdminActorContext actor = resolveAdminActorContext(httpServletRequest);
    adminApiRateLimitService.enforce(actor.getSessionId());
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
    adminApiRateLimitService.enforce(session.getId());
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

    Object principalName = session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
    if (!(principalName instanceof String principalEmail)
        || principalEmail.isBlank()
        || !adminEmail.equals(principalEmail)) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication context mismatch");
    }

    return AdminActorContext.of(
        memberIdNumber.longValue(),
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
