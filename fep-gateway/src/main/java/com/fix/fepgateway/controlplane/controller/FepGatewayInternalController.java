package com.fix.fepgateway.controlplane.controller;

import com.fix.common.error.ApiResponse;
import com.fix.common.validation.ContractPatterns;
import com.fix.common.web.CommonHeaders;
import com.fix.fepgateway.controlplane.service.FepGatewayControlService;
import com.fix.fepgateway.dto.request.FepInternalOrderStatusRequest;
import com.fix.fepgateway.dto.response.FepOrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/fep-internal/v1/orders")
public class FepGatewayInternalController {

  private final FepGatewayControlService fepGatewayControlService;

  public FepGatewayInternalController(FepGatewayControlService fepGatewayControlService) {
    this.fepGatewayControlService = fepGatewayControlService;
  }

  @PostMapping("/{clOrdId}/status")
  public ApiResponse<FepOrderResponse> updateStatus(
      @RequestHeader(CommonHeaders.X_INTERNAL_SECRET) String internalSecret,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Pattern(regexp = ContractPatterns.UUID_V4)
      @RequestHeader(CommonHeaders.X_CL_ORD_ID) String clOrdIdHeader,
      @Pattern(regexp = ContractPatterns.UUID_V4)
      @PathVariable String clOrdId,
      @Valid @RequestBody FepInternalOrderStatusRequest request
  ) {
    ClOrdIdHeaderValidator.requireExactMatch(clOrdIdHeader, clOrdId);
    return ApiResponse.success(
        FepOrderResponse.from(fepGatewayControlService.internalUpdateStatus(request.toVo(clOrdId)))
    );
  }
}
