package com.fix.corebank.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FepGatewayReplayResponse(
    String clOrdId,
    String finalStatus,
    String executionResult,
    String executionSource,
    Long executedQty,
    Long executedPrice,
    String processedBy,
    Instant processedAt
) {
}
