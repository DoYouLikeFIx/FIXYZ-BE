package com.fix.corebank.vo;

public class InternalOrderSnapshotResult {

  private final Long orderId;
  private final Long accountId;
  private final String clOrdId;
  private final String status;
  private final String externalSyncStatus;
  private final String externalOrderId;

  private InternalOrderSnapshotResult(
      Long orderId,
      Long accountId,
      String clOrdId,
      String status,
      String externalSyncStatus,
      String externalOrderId
  ) {
    this.orderId = orderId;
    this.accountId = accountId;
    this.clOrdId = clOrdId;
    this.status = status;
    this.externalSyncStatus = externalSyncStatus;
    this.externalOrderId = externalOrderId;
  }

  public static InternalOrderSnapshotResult of(
      Long orderId,
      Long accountId,
      String clOrdId,
      String status,
      String externalSyncStatus,
      String externalOrderId
  ) {
    return new InternalOrderSnapshotResult(orderId, accountId, clOrdId, status, externalSyncStatus, externalOrderId);
  }

  public Long getOrderId() {
    return orderId;
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getStatus() {
    return status;
  }

  public String getExternalSyncStatus() {
    return externalSyncStatus;
  }

  public String getExternalOrderId() {
    return externalOrderId;
  }
}
