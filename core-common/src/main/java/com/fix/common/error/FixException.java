package com.fix.common.error;

public class FixException extends RuntimeException {
  private final ErrorCode errorCode;
  private final ErrorMetadata metadata;

  public FixException(ErrorCode errorCode, String message) {
    this(errorCode, message, null);
  }

  public FixException(ErrorCode errorCode, String message, ErrorMetadata metadata) {
    super(message);
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
