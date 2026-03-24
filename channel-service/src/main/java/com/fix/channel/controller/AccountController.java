package com.fix.channel.controller;

import com.fix.channel.dto.request.AccountPositionQueryRequest;
import com.fix.channel.dto.request.AccountOrderHistoryQueryRequest;
import com.fix.channel.dto.response.AccountOrderHistoryResponse;
import com.fix.channel.dto.response.ApiResponseAccountSummaryResponse;
import com.fix.channel.dto.response.AccountPositionResponse;
import com.fix.channel.dto.response.AccountSummaryResponse;
import com.fix.channel.service.AccountOrderHistoryService;
import com.fix.channel.service.AccountPositionService;
import com.fix.channel.vo.AccountPositionsQueryCommand;
import com.fix.channel.vo.AccountSummaryQueryCommand;
import com.fix.common.error.ApiResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

  private final AccountPositionService accountPositionService;
  private final AccountOrderHistoryService accountOrderHistoryService;

  public AccountController(
      AccountPositionService accountPositionService,
      AccountOrderHistoryService accountOrderHistoryService
  ) {
    this.accountPositionService = accountPositionService;
    this.accountOrderHistoryService = accountOrderHistoryService;
  }

  @GetMapping("/{accountId}/positions")
  @Operation(summary = "Get account position")
  public ApiResponse<AccountPositionResponse> getPosition(
      @PathVariable Long accountId,
      @Validated @ParameterObject @ModelAttribute AccountPositionQueryRequest request,
      HttpServletRequest httpServletRequest
  ) {
    Long memberId = resolveAuthenticatedMemberId(httpServletRequest);
    return ApiResponse.success(AccountPositionResponse.from(
        accountPositionService.getAccountPosition(request.toVo(accountId, memberId))
    ));
  }

  @GetMapping("/{accountId}/positions/list")
  @Operation(summary = "Get owned account positions")
  public ApiResponse<List<AccountPositionResponse>> getPositions(
      @PathVariable Long accountId,
      HttpServletRequest httpServletRequest
  ) {
    Long memberId = resolveAuthenticatedMemberId(httpServletRequest);
    return ApiResponse.success(
        accountPositionService.getAccountPositions(
                AccountPositionsQueryCommand.of(accountId, memberId)
            ).stream()
            .map(AccountPositionResponse::from)
            .toList()
    );
  }

  @GetMapping("/{accountId}/summary")
  @Operation(
      summary = "Get account summary",
      responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          content = @Content(schema = @Schema(implementation = ApiResponseAccountSummaryResponse.class))
      )
  )
  public ApiResponseAccountSummaryResponse getSummary(
      @PathVariable Long accountId,
      HttpServletRequest httpServletRequest
  ) {
    Long memberId = resolveAuthenticatedMemberId(httpServletRequest);
    return ApiResponseAccountSummaryResponse.success(AccountSummaryResponse.from(
        accountPositionService.getAccountSummary(AccountSummaryQueryCommand.of(accountId, memberId))
    ));
  }
  @GetMapping("/{accountId}/orders")
  @Operation(summary = "Get account order history")
  public ApiResponse<AccountOrderHistoryResponse> getOrderHistory(
      @PathVariable Long accountId,
      @Validated @ParameterObject @ModelAttribute AccountOrderHistoryQueryRequest request,
      HttpServletRequest httpServletRequest
  ) {
    Long memberId = resolveAuthenticatedMemberId(httpServletRequest);
    return ApiResponse.success(AccountOrderHistoryResponse.from(
        accountOrderHistoryService.getAccountOrderHistory(request.toVo(accountId, memberId))
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
