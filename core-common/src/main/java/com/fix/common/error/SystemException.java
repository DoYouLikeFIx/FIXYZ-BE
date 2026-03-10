package com.fix.common.error;

public class SystemException extends RuntimeException {

  private final ErrorCode errorCode;
  private final ErrorMetadata metadata;

  public SystemException(ErrorCode errorCode) {
    this(errorCode, errorCode.defaultMessage());
  }

  public SystemException(ErrorCode errorCode, String message) {
    this(errorCode, message, null, null);
  }

  public SystemException(ErrorCode errorCode, String message, Throwable cause) {
    this(errorCode, message, cause, null);
  }

  public SystemException(ErrorCode errorCode, String message, ErrorMetadata metadata) {
    this(errorCode, message, null, metadata);
  }

  public SystemException(ErrorCode errorCode, String message, Throwable cause, ErrorMetadata metadata) {
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
