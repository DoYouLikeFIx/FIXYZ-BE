package com.fix.channel.controller;

import com.fix.channel.dto.request.MemberPasswordUpdateRequest;
import com.fix.channel.dto.request.MemberProfileUpdateRequest;
import com.fix.channel.dto.response.MemberProfileResponse;
import com.fix.channel.service.MemberService;
import com.fix.common.error.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

  private final MemberService memberService;

  public MemberController(MemberService memberService) {
    this.memberService = memberService;
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
}
