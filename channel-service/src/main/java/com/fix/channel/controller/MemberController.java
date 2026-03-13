package com.fix.channel.controller;

import com.fix.channel.dto.request.MemberPasswordUpdateRequest;
import com.fix.channel.dto.request.MemberProfileUpdateRequest;
import com.fix.channel.dto.request.MemberTotpRebindRequest;
import com.fix.channel.dto.request.TotpConfirmRequest;
import com.fix.channel.dto.request.TotpEnrollRequest;
import com.fix.channel.dto.response.MfaRecoveryRebindConfirmResponse;
import com.fix.channel.dto.response.MemberProfileResponse;
import com.fix.channel.dto.response.OtpVerifyResponse;
import com.fix.channel.dto.response.TotpRebindBootstrapResponse;
import com.fix.channel.dto.response.TotpEnrollResponse;
import com.fix.channel.service.AuthService;
import com.fix.channel.service.MfaRecoveryService;
import com.fix.channel.service.MemberService;
import com.fix.common.error.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

  private final MemberService memberService;
  private final AuthService authService;
  private final MfaRecoveryService mfaRecoveryService;

  public MemberController(MemberService memberService, AuthService authService, MfaRecoveryService mfaRecoveryService) {
    this.memberService = memberService;
    this.authService = authService;
    this.mfaRecoveryService = mfaRecoveryService;
  }

  @GetMapping("/me")
  public ApiResponse<MemberProfileResponse> readMyProfile(HttpServletRequest request) {
    return ApiResponse.success(MemberProfileResponse.from(memberService.readMyProfile(request)));
  }

  @PatchMapping("/me")
  public ApiResponse<MemberProfileResponse> updateMyProfile(
      @Valid @ModelAttribute MemberProfileUpdateRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(MemberProfileResponse.from(
        memberService.updateMyProfile(request.toVo(), httpServletRequest)
    ));
  }

  @PatchMapping("/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateMyPassword(
      @Valid @ModelAttribute MemberPasswordUpdateRequest request,
      HttpServletRequest httpServletRequest
  ) {
    memberService.updateMyPassword(request.toVo(), httpServletRequest);
  }

  @PostMapping("/me/totp/enroll")
  public ApiResponse<TotpEnrollResponse> enrollTotp(
      @Valid @RequestBody TotpEnrollRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(TotpEnrollResponse.from(authService.enrollTotp(request.toVo(), httpServletRequest)));
  }

  @PostMapping("/me/totp/confirm")
  public ApiResponse<OtpVerifyResponse> confirmTotp(
      @Valid @RequestBody TotpConfirmRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(OtpVerifyResponse.from(authService.confirmTotp(request.toVo(), httpServletRequest)));
  }

  @PostMapping("/me/totp/rebind")
  public ApiResponse<TotpRebindBootstrapResponse> rebindTotp(
      @Valid @RequestBody MemberTotpRebindRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(TotpRebindBootstrapResponse.from(
        mfaRecoveryService.bootstrapAuthenticated(request.toVo(), httpServletRequest)
    ));
  }
}
