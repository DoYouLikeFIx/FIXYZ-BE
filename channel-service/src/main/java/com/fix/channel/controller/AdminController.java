package com.fix.channel.controller;

import com.fix.channel.dto.request.AdminAccountStatusTransitionRequest;
import com.fix.channel.dto.request.AdminSecurityEventRequest;
import com.fix.channel.dto.response.AdminAccountStatusTransitionResponse;
import com.fix.channel.dto.response.AdminSecurityEventResponse;
import com.fix.channel.service.AdminAccountStatusService;
import com.fix.channel.service.ChannelScaffoldService;
import com.fix.common.error.ApiResponse;
import jakarta.validation.Valid;
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

  public AdminController(
      ChannelScaffoldService channelScaffoldService,
      AdminAccountStatusService adminAccountStatusService
  ) {
    this.channelScaffoldService = channelScaffoldService;
    this.adminAccountStatusService = adminAccountStatusService;
  }

  @GetMapping("/security-events")
  public ApiResponse<AdminSecurityEventResponse> securityEvents(
      @Valid @ModelAttribute AdminSecurityEventRequest request
  ) {
    return ApiResponse.success(AdminSecurityEventResponse.from(channelScaffoldService.getSecurityEvents(request.toVo())));
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
}
