package com.fix.fepgateway.controlplane.controller;

import com.fix.common.error.ApiResponse;
import com.fix.common.web.CommonHeaders;
import com.fix.fepgateway.controlplane.service.FepGatewayControlService;
import com.fix.fepgateway.dto.request.FepOrderCancelRequest;
import com.fix.fepgateway.dto.request.FepOrderReplayRequest;
import com.fix.fepgateway.dto.request.FepOrderStatusRequest;
import com.fix.fepgateway.dto.request.FepOrderSubmitRequest;
import com.fix.fepgateway.dto.response.FepOrderResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fep/v1/orders")
public class FepGatewayOrderController {

  private final FepGatewayControlService fepGatewayControlService;

  public FepGatewayOrderController(FepGatewayControlService fepGatewayControlService) {
    this.fepGatewayControlService = fepGatewayControlService;
  }

  @PostMapping
  public ApiResponse<FepOrderResponse> submit(
      @RequestHeader(CommonHeaders.X_CL_ORD_ID) String clOrdIdHeader,
      @Valid @ModelAttribute FepOrderSubmitRequest request
  ) {
    ClOrdIdHeaderValidator.requireExactMatch(clOrdIdHeader, request.getClOrdId());
    return ApiResponse.success(FepOrderResponse.from(fepGatewayControlService.submitOrder(request.toVo())));
  }

  @GetMapping("/{clOrdId}/status")
  public ApiResponse<FepOrderResponse> status(
      @RequestHeader(CommonHeaders.X_CL_ORD_ID) String clOrdIdHeader,
      @PathVariable String clOrdId,
      @ModelAttribute FepOrderStatusRequest request
  ) {
    ClOrdIdHeaderValidator.requireExactMatch(clOrdIdHeader, clOrdId);
    return ApiResponse.success(FepOrderResponse.from(fepGatewayControlService.status(request.toVo(clOrdId))));
  }

  @PostMapping("/{clOrdId}/cancel")
  public ApiResponse<FepOrderResponse> cancel(
      @RequestHeader(CommonHeaders.X_CL_ORD_ID) String clOrdIdHeader,
      @PathVariable String clOrdId,
      @Valid @ModelAttribute FepOrderCancelRequest request
  ) {
    ClOrdIdHeaderValidator.requireExactMatch(clOrdIdHeader, clOrdId, request.getClOrdId());
    return ApiResponse.success(FepOrderResponse.from(fepGatewayControlService.cancel(request.toVo(clOrdId))));
  }

  @PostMapping("/{clOrdId}/replay")
  public ApiResponse<FepOrderResponse> replay(
      @RequestHeader(CommonHeaders.X_CL_ORD_ID) String clOrdIdHeader,
      @PathVariable String clOrdId,
      @Valid @ModelAttribute FepOrderReplayRequest request
  ) {
    ClOrdIdHeaderValidator.requireExactMatch(clOrdIdHeader, clOrdId, request.getClOrdId());
    return ApiResponse.success(FepOrderResponse.from(fepGatewayControlService.replay(request.toVo(clOrdId))));
  }
}
