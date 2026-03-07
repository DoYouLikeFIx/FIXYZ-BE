package com.fix.channel.controller;

import com.fix.channel.dto.request.AuthLoginRequest;
import com.fix.channel.dto.request.CsrfBootstrapRequest;
import com.fix.channel.dto.request.OtpVerifyRequest;
import com.fix.channel.dto.response.AuthLoginResponse;
import com.fix.channel.dto.response.CsrfBootstrapResponse;
import com.fix.channel.dto.response.OtpVerifyResponse;
import com.fix.channel.service.ChannelScaffoldService;
import com.fix.common.error.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final ChannelScaffoldService channelScaffoldService;

  public AuthController(ChannelScaffoldService channelScaffoldService) {
    this.channelScaffoldService = channelScaffoldService;
  }

  @GetMapping("/csrf")
  public ApiResponse<CsrfBootstrapResponse> bootstrapCsrf(
      @ModelAttribute CsrfBootstrapRequest request,
      CsrfToken csrfToken
  ) {
    return ApiResponse.success(CsrfBootstrapResponse.from(channelScaffoldService.bootstrapCsrf(request.toVo(), csrfToken)));
  }

  @PostMapping("/login")
  public ApiResponse<AuthLoginResponse> login(@Valid @ModelAttribute AuthLoginRequest request) {
    return ApiResponse.success(AuthLoginResponse.from(channelScaffoldService.login(request.toVo())));
  }

  @PostMapping("/otp/verify")
  public ApiResponse<OtpVerifyResponse> verifyOtp(@Valid @ModelAttribute OtpVerifyRequest request) {
    return ApiResponse.success(OtpVerifyResponse.from(channelScaffoldService.verifyOtp(request.toVo())));
  }
}
