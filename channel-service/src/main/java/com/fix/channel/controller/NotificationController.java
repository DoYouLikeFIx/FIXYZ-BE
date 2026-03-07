package com.fix.channel.controller;

import com.fix.channel.dto.request.NotificationStreamRequest;
import com.fix.channel.dto.response.NotificationStreamResponse;
import com.fix.channel.service.ChannelScaffoldService;
import com.fix.common.error.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final ChannelScaffoldService channelScaffoldService;

  public NotificationController(ChannelScaffoldService channelScaffoldService) {
    this.channelScaffoldService = channelScaffoldService;
  }

  @GetMapping("/stream")
  public ApiResponse<NotificationStreamResponse> stream(@Valid @ModelAttribute NotificationStreamRequest request) {
    return ApiResponse.success(NotificationStreamResponse.from(channelScaffoldService.streamNotifications(request.toVo())));
  }
}
