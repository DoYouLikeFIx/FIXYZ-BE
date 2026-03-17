package com.fix.channel.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fix.channel.dto.request.NotificationStreamRequest;
import com.fix.channel.dto.response.NotificationItemResponse;
import com.fix.channel.dto.response.NotificationStreamResponse;
import com.fix.channel.service.ChannelScaffoldService;
import com.fix.common.error.ApiResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private static final String AUTH_MEMBER_ID = "AUTH_MEMBER_ID";

  private final ChannelScaffoldService channelScaffoldService;

  public NotificationController(ChannelScaffoldService channelScaffoldService) {
    this.channelScaffoldService = channelScaffoldService;
  }

  @GetMapping
  public ApiResponse<NotificationStreamResponse> list(
      @Valid @ModelAttribute NotificationStreamRequest request,
      HttpServletRequest httpServletRequest
  ) {
    Long memberId = resolveAuthenticatedMemberId(httpServletRequest);
    return ApiResponse.success(NotificationStreamResponse.from(channelScaffoldService.streamNotifications(request.toVo(memberId))));
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<String> stream(
      @Valid @ModelAttribute NotificationStreamRequest request,
      HttpServletRequest httpServletRequest
  ) {
    // Keep heartbeat SSE contract for existing clients while list/read APIs use persisted history.
    resolveAuthenticatedMemberId(httpServletRequest);
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .body("event:heartbeat\\ndata:ok\\n\\n");
  }

  @PatchMapping("/{notificationId}/read")
  public ApiResponse<NotificationItemResponse> markRead(
      @PathVariable @Min(1) Long notificationId,
      HttpServletRequest httpServletRequest
  ) {
    Long memberId = resolveAuthenticatedMemberId(httpServletRequest);
    return ApiResponse.success(NotificationItemResponse.from(
        channelScaffoldService.markNotificationRead(memberId, notificationId)
    ));
  }

  private Long resolveAuthenticatedMemberId(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }

    Object memberIdAttr = session.getAttribute(AUTH_MEMBER_ID);
    if (!(memberIdAttr instanceof Long memberId)) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }
    return memberId;
  }
}
