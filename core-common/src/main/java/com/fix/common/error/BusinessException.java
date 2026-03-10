package com.fix.common.error;

public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final ErrorMetadata metadata;

  public BusinessException(ErrorCode errorCode) {
    this(errorCode, errorCode.defaultMessage());
  }

  public BusinessException(ErrorCode errorCode, String message) {
    this(errorCode, message, null, null);
  }

  public BusinessException(ErrorCode errorCode, String message, ErrorMetadata metadata) {
    this(errorCode, message, null, metadata);
  }

  public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
    this(errorCode, message, cause, null);
  }

  public BusinessException(ErrorCode errorCode, String message, Throwable cause, ErrorMetadata metadata) {
    super(message, cause);
    this.errorCode = errorCode;
    this.metadata = metadata;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public ErrorMetadata getMetadata() {
    return metadata;
  }
}
