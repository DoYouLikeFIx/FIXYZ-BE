package com.fix.channel.dto.response;

import com.fix.channel.vo.AdminOrderIdempotencyReconciliationResult;

public record AdminOrderIdempotencyReconciliationResponse(
    String clOrdId,
    String orderSessionId,
    String outcome,
    String mismatchType,
    String externalOrderId,
    String externalSyncStatus,
    String message,
    int scanned,
    int restored,
    int mismatched,
    int failed
) {

  public static AdminOrderIdempotencyReconciliationResponse from(AdminOrderIdempotencyReconciliationResult result) {
    return new AdminOrderIdempotencyReconciliationResponse(
        result.getClOrdId(),
        result.getOrderSessionId(),
        result.getOutcome(),
        result.getMismatchType(),
        result.getExternalOrderId(),
        result.getExternalSyncStatus(),
        result.getMessage(),
        result.getScanned(),
        result.getRestored(),
        result.getMismatched(),
        result.getFailed()
    );
  }
}
