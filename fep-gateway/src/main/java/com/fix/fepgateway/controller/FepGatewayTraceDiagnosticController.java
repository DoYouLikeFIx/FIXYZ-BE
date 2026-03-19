package com.fix.fepgateway.controller;

import com.fix.common.error.ApiResponse;
import com.fix.common.web.CommonHeaders;
import com.fix.fepgateway.dataplane.fix.FepSimulatorTraceBridgeClient;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
public class FepGatewayTraceDiagnosticController {

  private final FepSimulatorTraceBridgeClient fepSimulatorTraceBridgeClient;

  public FepGatewayTraceDiagnosticController(FepSimulatorTraceBridgeClient fepSimulatorTraceBridgeClient) {
    this.fepSimulatorTraceBridgeClient = fepSimulatorTraceBridgeClient;
  }

  @GetMapping("/fep-internal/v1/diagnostics/trace-forwarding/simulator")
  public ApiResponse<FepSimulatorTraceBridgeClient.TraceBridgeResult> forwardTraceToSimulator(
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @RequestHeader(CommonHeaders.TRACEPARENT) String traceparent
  ) {
    return ApiResponse.success(fepSimulatorTraceBridgeClient.bridgeTrace(correlationId, traceparent));
  }
}
