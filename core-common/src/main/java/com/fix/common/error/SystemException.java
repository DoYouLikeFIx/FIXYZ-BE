package com.fix.common.error;

public class SystemException extends RuntimeException {

  private final ErrorCode errorCode;

  public SystemException(ErrorCode errorCode) {
    this(errorCode, errorCode.defaultMessage());
  }

  public SystemException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public SystemException(ErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
