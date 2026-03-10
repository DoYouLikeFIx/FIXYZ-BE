package com.fix.fepgateway.vo;

public class GatewayInternalOrderStatusCommand {

  private final String clOrdId;
  private final String status;
  private final String message;
  private final String rejectReason;
  private final String parseError;
  private final Long executedQty;
  private final Long executedPrice;
  private final String recoveryStatus;
  private final String requeryStatus;
  private final Long requeryExecutedQty;
  private final Long requeryExecutedPrice;
  private final String cancelFailureMode;
  private final Long referencePrice;

  private GatewayInternalOrderStatusCommand(
      String clOrdId,
      String status,
      String message,
      String rejectReason,
      String parseError,
      Long executedQty,
      Long executedPrice,
      String recoveryStatus,
      String requeryStatus,
      Long requeryExecutedQty,
      Long requeryExecutedPrice,
      String cancelFailureMode,
      Long referencePrice
  ) {
    this.clOrdId = clOrdId;
    this.status = status;
    this.message = message;
    this.rejectReason = rejectReason;
    this.parseError = parseError;
    this.executedQty = executedQty;
    this.executedPrice = executedPrice;
    this.recoveryStatus = recoveryStatus;
    this.requeryStatus = requeryStatus;
    this.requeryExecutedQty = requeryExecutedQty;
    this.requeryExecutedPrice = requeryExecutedPrice;
    this.cancelFailureMode = cancelFailureMode;
    this.referencePrice = referencePrice;
  }

  public static GatewayInternalOrderStatusCommand of(
      String clOrdId,
      String status,
      String message,
      String rejectReason,
      String parseError,
      Long executedQty,
      Long executedPrice,
      String recoveryStatus,
      String requeryStatus,
      Long requeryExecutedQty,
      Long requeryExecutedPrice,
      String cancelFailureMode,
      Long referencePrice
  ) {
    return new GatewayInternalOrderStatusCommand(
        clOrdId,
        status,
        message,
        rejectReason,
        parseError,
        executedQty,
        executedPrice,
        recoveryStatus,
        requeryStatus,
        requeryExecutedQty,
        requeryExecutedPrice,
        cancelFailureMode,
        referencePrice
    );
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getStatus() {
    return status;
  }

  public Long getExecutedQty() {
    return executedQty;
  }

  public String getMessage() {
    return message;
  }

  public String getRejectReason() {
    return rejectReason;
  }

  public String getParseError() {
    return parseError;
  }

  public Long getExecutedPrice() {
    return executedPrice;
  }

  public String getRecoveryStatus() {
    return recoveryStatus;
  }

  public String getRequeryStatus() {
    return requeryStatus;
  }

  public Long getRequeryExecutedQty() {
    return requeryExecutedQty;
  }

  public Long getRequeryExecutedPrice() {
    return requeryExecutedPrice;
  }

  public String getCancelFailureMode() {
    return cancelFailureMode;
  }

  public Long getReferencePrice() {
    return referencePrice;
  }
}
