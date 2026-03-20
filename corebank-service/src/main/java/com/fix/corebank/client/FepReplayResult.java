package com.fix.corebank.client;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.validation.ContractPatterns;
import java.time.Instant;

public record FepReplayResult(
    String clOrdId,
    String finalStatus,
    String executionResult,
    String executionSource,
    Long executedQty,
    Long executedPrice,
    String processedBy,
    Instant processedAt
) {

  public static FepReplayResult fromResponse(FepGatewayReplayResponse response, String expectedClOrdId) {
    require(!isBlank(response.clOrdId()), "clOrdId is required in replay response");
    require(expectedClOrdId.equals(response.clOrdId()), "replay response clOrdId must match request");
    require(!isBlank(response.finalStatus()), "finalStatus is required in replay response");
    require(!isBlank(response.processedBy()), "processedBy is required in replay response");
    require(ContractPatterns.isUuidV4(response.processedBy()), "processedBy must be a UUID v4");
    require(response.processedAt() != null, "processedAt is required in replay response");
    return new FepReplayResult(
        response.clOrdId(),
        response.finalStatus(),
        response.executionResult(),
        response.executionSource(),
        response.executedQty(),
        response.executedPrice(),
        response.processedBy(),
        response.processedAt()
    );
  }

  private static void require(boolean expression, String message) {
    if (!expression) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, message);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
