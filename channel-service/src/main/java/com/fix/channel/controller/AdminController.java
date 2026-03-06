package com.fix.channel.controller;

import com.fix.channel.dto.request.AdminSecurityEventRequest;
import com.fix.channel.dto.response.AdminSecurityEventResponse;
import com.fix.channel.service.ChannelScaffoldService;
import com.fix.common.error.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

  private final ChannelScaffoldService channelScaffoldService;

  public AdminController(ChannelScaffoldService channelScaffoldService) {
    this.channelScaffoldService = channelScaffoldService;
  }

  @GetMapping("/security-events")
  public ApiResponse<AdminSecurityEventResponse> securityEvents(
      @Valid @ModelAttribute AdminSecurityEventRequest request
  ) {
    return ApiResponse.success(AdminSecurityEventResponse.from(channelScaffoldService.getSecurityEvents(request.toVo())));
  }
}
