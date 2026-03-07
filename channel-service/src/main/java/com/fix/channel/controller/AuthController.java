package com.fix.channel.controller;

import com.fix.channel.dto.request.AuthLoginRequest;
import com.fix.channel.dto.request.AuthRegisterRequest;
import com.fix.channel.dto.request.CsrfBootstrapRequest;
import com.fix.channel.dto.request.OtpVerifyRequest;
import com.fix.channel.dto.response.AuthLoginResponse;
import com.fix.channel.dto.response.AuthLogoutResponse;
import com.fix.channel.dto.response.AuthRegisterResponse;
import com.fix.channel.dto.response.CsrfBootstrapResponse;
import com.fix.channel.dto.response.OtpVerifyResponse;
import com.fix.channel.service.AuthService;
import com.fix.channel.service.ChannelScaffoldService;
import com.fix.common.error.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final ChannelScaffoldService channelScaffoldService;
  private final String sessionCookieName;
  private final boolean sessionCookieHttpOnly;
  private final String sessionCookieSameSite;
  private final boolean sessionCookieSecure;

  public AuthController(
      AuthService authService,
      ChannelScaffoldService channelScaffoldService,
      @Value("${server.servlet.session.cookie.name:SESSION}") String sessionCookieName,
      @Value("${server.servlet.session.cookie.http-only:true}") boolean sessionCookieHttpOnly,
      @Value("${server.servlet.session.cookie.same-site:strict}") String sessionCookieSameSite,
      @Value("${server.servlet.session.cookie.secure:false}") boolean sessionCookieSecure
  ) {
    this.authService = authService;
    this.channelScaffoldService = channelScaffoldService;
    this.sessionCookieName = sessionCookieName;
    this.sessionCookieHttpOnly = sessionCookieHttpOnly;
    this.sessionCookieSameSite = sessionCookieSameSite;
    this.sessionCookieSecure = sessionCookieSecure;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AuthRegisterResponse> register(@Valid @ModelAttribute AuthRegisterRequest request) {
    return ApiResponse.success(AuthRegisterResponse.from(authService.register(request.toVo())));
  }

  @GetMapping("/csrf")
  public ApiResponse<CsrfBootstrapResponse> bootstrapCsrf(
      @ModelAttribute CsrfBootstrapRequest request,
      CsrfToken csrfToken
  ) {
    return ApiResponse.success(CsrfBootstrapResponse.from(channelScaffoldService.bootstrapCsrf(request.toVo(), csrfToken)));
  }

  @PostMapping("/login")
  public ApiResponse<AuthLoginResponse> login(
      @Valid @ModelAttribute AuthLoginRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(AuthLoginResponse.from(authService.login(request.toVo(), httpServletRequest)));
  }

  @PostMapping("/logout")
  public ApiResponse<AuthLogoutResponse> logout(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse
  ) {
    authService.logout(httpServletRequest);

    ResponseCookie expiredCookie = ResponseCookie.from(sessionCookieName, "")
        .path("/")
        .httpOnly(sessionCookieHttpOnly)
        .secure(sessionCookieSecure)
        .sameSite(sessionCookieSameSite)
        .maxAge(0)
        .build();
    httpServletResponse.addHeader("Set-Cookie", expiredCookie.toString());

    return ApiResponse.success(AuthLogoutResponse.of("logout completed"));
  }

  @PostMapping("/otp/verify")
  public ApiResponse<OtpVerifyResponse> verifyOtp(@Valid @ModelAttribute OtpVerifyRequest request) {
    return ApiResponse.success(OtpVerifyResponse.from(channelScaffoldService.verifyOtp(request.toVo())));
  }
}
