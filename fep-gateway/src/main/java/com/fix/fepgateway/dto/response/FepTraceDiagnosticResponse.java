package com.fix.fepgateway.dto.response;

import com.fix.fepgateway.dataplane.fix.FepSimulatorTraceBridgeClient;

public record FepTraceDiagnosticResponse(
    String correlationId,
    String traceparent,
    boolean forwarded,
    String message
) {

  public static FepTraceDiagnosticResponse from(FepSimulatorTraceBridgeClient.TraceBridgeResult result) {
    return new FepTraceDiagnosticResponse(
        result.correlationId(),
        result.traceparent(),
        result.forwarded(),
        result.message()
    );
  }
}
