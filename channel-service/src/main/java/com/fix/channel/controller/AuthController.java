package com.fix.channel.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fix.channel.dto.request.AuthLoginRequest;
import com.fix.channel.dto.request.AuthRegisterRequest;
import com.fix.channel.dto.request.CsrfBootstrapRequest;
import com.fix.channel.dto.request.OtpVerifyRequest;
import com.fix.channel.dto.request.PasswordForgotChallengeRequest;
import com.fix.channel.dto.request.PasswordForgotRequest;
import com.fix.channel.dto.request.PasswordResetRequest;
import com.fix.channel.dto.response.AuthLoginResponse;
import com.fix.channel.dto.response.AuthLogoutResponse;
import com.fix.channel.dto.response.AuthRegisterResponse;
import com.fix.channel.dto.response.AuthSessionResponse;
import com.fix.channel.dto.response.CsrfBootstrapResponse;
import com.fix.channel.dto.response.OtpVerifyResponse;
import com.fix.channel.dto.response.PasswordForgotChallengeResponse;
import com.fix.channel.dto.response.PasswordForgotResponse;
import com.fix.channel.service.AuthService;
import com.fix.channel.service.ChannelScaffoldService;
import com.fix.channel.service.PasswordRecoveryService;
import com.fix.common.error.ApiResponse;
import com.fix.common.web.CommonHeaders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final ChannelScaffoldService channelScaffoldService;
  private final PasswordRecoveryService passwordRecoveryService;

  public AuthController(
      AuthService authService,
      ChannelScaffoldService channelScaffoldService,
      PasswordRecoveryService passwordRecoveryService
  ) {
    this.authService = authService;
    this.channelScaffoldService = channelScaffoldService;
    this.passwordRecoveryService = passwordRecoveryService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AuthRegisterResponse> register(
      @Valid @ModelAttribute AuthRegisterRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(AuthRegisterResponse.from(
        authService.register(request.toVo(), resolveCorrelationId(httpServletRequest))
    ));
  }

  @GetMapping("/csrf")
  public ApiResponse<CsrfBootstrapResponse> bootstrapCsrf(
      @ModelAttribute CsrfBootstrapRequest request,
      HttpServletRequest httpServletRequest
  ) {
    // 스프링 시큐리티가 request attribute로 주입한 CSRF 토큰을 직접 조회한다.
    CsrfToken csrfToken = (CsrfToken) httpServletRequest.getAttribute(CsrfToken.class.getName());
    if (csrfToken == null) {
      csrfToken = (CsrfToken) httpServletRequest.getAttribute("_csrf");
    }
    if (csrfToken == null) {
      throw new IllegalStateException("CSRF token is not available");
    }
    return ApiResponse.success(CsrfBootstrapResponse.from(channelScaffoldService.bootstrapCsrf(request.toVo(), csrfToken)));
  }

  @PostMapping("/login")
  public ApiResponse<AuthLoginResponse> login(
      @Valid @ModelAttribute AuthLoginRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(AuthLoginResponse.from(authService.login(request.toVo(), httpServletRequest)));
  }

  @GetMapping("/session")
  public ApiResponse<AuthSessionResponse> currentSession(HttpServletRequest httpServletRequest) {
    return ApiResponse.success(AuthSessionResponse.from(authService.currentSession(httpServletRequest)));
  }

  @PostMapping("/logout")
  public ApiResponse<AuthLogoutResponse> logout(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse
  ) {
    ResponseCookie expiredCookie = authService.logout(httpServletRequest);
    httpServletResponse.addHeader("Set-Cookie", expiredCookie.toString());

    return ApiResponse.success(AuthLogoutResponse.of("logout completed"));
  }

  @PostMapping("/otp/verify")
  public ApiResponse<OtpVerifyResponse> verifyOtp(
      @Valid @ModelAttribute OtpVerifyRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(OtpVerifyResponse.from(
        channelScaffoldService.verifyOtp(request.toVo(), httpServletRequest)
    ));
  }

  @PostMapping("/password/forgot")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiResponse<PasswordForgotResponse> forgotPassword(
      @Valid @RequestBody PasswordForgotRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(PasswordForgotResponse.from(
        passwordRecoveryService.forgot(request.toVo(), httpServletRequest)
    ));
  }

  @PostMapping("/password/forgot/challenge")
  public ApiResponse<PasswordForgotChallengeResponse> bootstrapForgotPasswordChallenge(
      @Valid @RequestBody PasswordForgotChallengeRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(PasswordForgotChallengeResponse.from(
        passwordRecoveryService.bootstrapChallenge(request.toVo(), httpServletRequest)
    ));
  }

  @PostMapping("/password/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(
      @Valid @RequestBody PasswordResetRequest request,
      HttpServletRequest httpServletRequest
  ) {
    passwordRecoveryService.reset(request.toVo(), httpServletRequest);
  }

  private String resolveCorrelationId(HttpServletRequest request) {
    String correlationId = request.getHeader(CommonHeaders.X_CORRELATION_ID);
    if (correlationId == null || correlationId.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return correlationId;
  }
}
