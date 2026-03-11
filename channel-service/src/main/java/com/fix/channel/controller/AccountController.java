package com.fix.channel.controller;

import com.fix.channel.dto.request.AccountPositionQueryRequest;
import com.fix.channel.dto.response.AccountPositionResponse;
import com.fix.channel.service.AccountPositionService;
import com.fix.common.error.ApiResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

  private final AccountPositionService accountPositionService;

  public AccountController(AccountPositionService accountPositionService) {
    this.accountPositionService = accountPositionService;
  }

  @GetMapping("/{accountId}/positions")
  public ApiResponse<AccountPositionResponse> getPosition(
      @PathVariable Long accountId,
      @Valid @ModelAttribute AccountPositionQueryRequest request,
      HttpServletRequest httpServletRequest
  ) {
    Long memberId = resolveAuthenticatedMemberId(httpServletRequest);
    return ApiResponse.success(AccountPositionResponse.from(
        accountPositionService.getAccountPosition(request.toVo(accountId, memberId))
    ));
  }

  private Long resolveAuthenticatedMemberId(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }

    Object memberIdAttr = session.getAttribute("AUTH_MEMBER_ID");
    if (!(memberIdAttr instanceof Number memberIdNumber)) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }
    return memberIdNumber.longValue();
  }
}
