package com.fix.fepgateway.controlplane.controller;

import com.fix.common.error.ApiResponse;
import com.fix.common.error.ApiErrorResponse;
import com.fix.common.validation.ContractPatterns;
import com.fix.common.web.CommonHeaders;
import com.fix.fepgateway.contract.validation.ClOrdIdContractValidator;
import com.fix.fepgateway.controlplane.service.FepGatewayControlService;
import com.fix.fepgateway.dto.request.FepOrderCancelRequest;
import com.fix.fepgateway.dto.request.FepOrderReplayRequest;
import com.fix.fepgateway.dto.request.FepOrderSubmitRequest;
import com.fix.fepgateway.dto.response.FepOrderCancelResponse;
import com.fix.fepgateway.dto.response.FepOrderResponse;
import com.fix.fepgateway.dto.response.FepOrderReplayResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/fep/v1/orders")
public class FepGatewayOrderController {

  private final FepGatewayControlService fepGatewayControlService;

  public FepGatewayOrderController(FepGatewayControlService fepGatewayControlService) {
    this.fepGatewayControlService = fepGatewayControlService;
  }

  @PostMapping
  @Operation(summary = "Submit an order")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Submit accepted"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "401",
          description = "AUTH_001 referenceId ownership violation",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
      ),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "422",
          description = "VALIDATION-001 request contract or idempotency policy violation",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
      )
  })
  public ApiResponse<FepOrderResponse> submit(
      @RequestHeader(CommonHeaders.X_INTERNAL_SECRET) String internalSecret,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Pattern(regexp = ContractPatterns.UUID_V4)
      @RequestHeader(CommonHeaders.X_CL_ORD_ID) String clOrdIdHeader,
      @Valid @RequestBody FepOrderSubmitRequest request
  ) {
    ClOrdIdContractValidator.requireExactMatch(clOrdIdHeader, request.clOrdId());
    return ApiResponse.success(FepOrderResponse.from(fepGatewayControlService.submitOrder(request.toVo())));
  }

  @GetMapping("/{clOrdId}/status")
  public ApiResponse<FepOrderResponse> status(
      @RequestHeader(CommonHeaders.X_INTERNAL_SECRET) String internalSecret,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Pattern(regexp = ContractPatterns.UUID_V4)
      @PathVariable String clOrdId
  ) {
    return ApiResponse.success(FepOrderResponse.from(fepGatewayControlService.status(
        com.fix.fepgateway.vo.GatewayOrderStatusCommand.of(clOrdId)
    )));
  }

  @PostMapping("/{clOrdId}/cancel")
  @Operation(summary = "Cancel an order")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cancel accepted"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "409",
          description = "9006 CANCEL_REJECTED",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
      ),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "504",
          description = "9004 TIMEOUT",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
      )
  })
  public ApiResponse<FepOrderCancelResponse> cancel(
      @RequestHeader(CommonHeaders.X_INTERNAL_SECRET) String internalSecret,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Pattern(regexp = ContractPatterns.UUID_V4)
      @PathVariable String clOrdId,
      @Valid @RequestBody FepOrderCancelRequest request
  ) {
    return ApiResponse.success(FepOrderCancelResponse.from(fepGatewayControlService.cancel(request.toVo(clOrdId))));
  }

  @PostMapping("/{clOrdId}/replay")
  @Operation(summary = "Replay an escalated order")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Replay processed"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "409",
          description = "9009 INVALID_SESSION_STATUS - replay target must be ESCALATED",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
      )
  })
  public ApiResponse<FepOrderReplayResponse> replay(
      @RequestHeader(CommonHeaders.X_INTERNAL_SECRET) String internalSecret,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Pattern(regexp = ContractPatterns.UUID_V4)
      @PathVariable String clOrdId,
      @Valid @RequestBody FepOrderReplayRequest request
  ) {
    return ApiResponse.success(FepOrderReplayResponse.from(fepGatewayControlService.replay(request.toVo(clOrdId))));
  }
}
