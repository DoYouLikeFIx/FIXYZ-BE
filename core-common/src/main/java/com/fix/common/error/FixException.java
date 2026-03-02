package com.fix.common.error;

public class FixException extends RuntimeException {
  private final ErrorCode errorCode;

  public FixException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
