package com.fix.channel.vo;

public class AdminOrderIdempotencyReconciliationResult {

  private final String clOrdId;
  private final String orderSessionId;
  private final String outcome;
  private final String mismatchType;
  private final String externalOrderId;
  private final String externalSyncStatus;
  private final String message;
  private final int scanned;
  private final int restored;
  private final int mismatched;
  private final int failed;

  private AdminOrderIdempotencyReconciliationResult(
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
    this.clOrdId = clOrdId;
    this.orderSessionId = orderSessionId;
    this.outcome = outcome;
    this.mismatchType = mismatchType;
    this.externalOrderId = externalOrderId;
    this.externalSyncStatus = externalSyncStatus;
    this.message = message;
    this.scanned = scanned;
    this.restored = restored;
    this.mismatched = mismatched;
    this.failed = failed;
  }

  public static AdminOrderIdempotencyReconciliationResult of(
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
    return new AdminOrderIdempotencyReconciliationResult(
        clOrdId,
        orderSessionId,
        outcome,
        mismatchType,
        externalOrderId,
        externalSyncStatus,
        message,
        scanned,
        restored,
        mismatched,
        failed
    );
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getOrderSessionId() {
    return orderSessionId;
  }

  public String getOutcome() {
    return outcome;
  }

  public String getMismatchType() {
    return mismatchType;
  }

  public String getExternalOrderId() {
    return externalOrderId;
  }

  public String getExternalSyncStatus() {
    return externalSyncStatus;
  }

  public String getMessage() {
    return message;
  }

  public int getScanned() {
    return scanned;
  }

  public int getRestored() {
    return restored;
  }

  public int getMismatched() {
    return mismatched;
  }

  public int getFailed() {
    return failed;
  }
}
