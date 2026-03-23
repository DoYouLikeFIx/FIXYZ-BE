package com.fix.corebank.dto.response;

import com.fix.corebank.vo.InternalOrderSnapshotResult;

public class InternalOrderSnapshotResponse {

  private final Long orderId;
  private final Long accountId;
  private final String clOrdId;
  private final String status;
  private final String externalSyncStatus;
  private final String externalOrderId;

  private InternalOrderSnapshotResponse(
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

  public static InternalOrderSnapshotResponse from(InternalOrderSnapshotResult result) {
    return new InternalOrderSnapshotResponse(
        result.getOrderId(),
        result.getAccountId(),
        result.getClOrdId(),
        result.getStatus(),
        result.getExternalSyncStatus(),
        result.getExternalOrderId()
    );
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
