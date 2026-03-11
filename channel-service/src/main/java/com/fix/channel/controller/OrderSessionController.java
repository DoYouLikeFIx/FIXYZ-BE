package com.fix.channel.controller;

import com.fix.channel.dto.request.OrderSessionCreateRequest;
import com.fix.channel.dto.request.OrderSessionQueryRequest;
import com.fix.channel.dto.response.OrderSessionResponse;
import com.fix.channel.service.OrderSessionService;
import com.fix.common.error.ApiResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/sessions")
public class OrderSessionController {

  private final OrderSessionService orderSessionService;

  public OrderSessionController(OrderSessionService orderSessionService) {
    this.orderSessionService = orderSessionService;
  }

  @Operation(summary = "Create or replay an order session")
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Existing active session replayed")
  })
  @PostMapping
  public ResponseEntity<ApiResponse<OrderSessionResponse>> create(
      @Valid @RequestBody OrderSessionCreateRequest request,
      HttpServletRequest httpServletRequest
  ) {
    var result = orderSessionService.createOrderSession(request.toVo(resolveAuthenticatedMemberId(httpServletRequest)));
    HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ApiResponse.success(OrderSessionResponse.from(result)));
  }

  @Operation(
      summary = "Get an order session",
      description = "Provide exactly one of orderSessionId or clOrdId. Do not send both."
  )
  @GetMapping
  public ApiResponse<OrderSessionResponse> get(
      @Validated @ParameterObject @ModelAttribute OrderSessionQueryRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(OrderSessionResponse.from(
        orderSessionService.getOrderSession(request.toVo(resolveAuthenticatedMemberId(httpServletRequest)))
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
