package com.fix.common.error;

import java.util.Map;

public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final ErrorMetadata metadata;
  private final Map<String, Object> details;

  public BusinessException(ErrorCode errorCode) {
    this(errorCode, errorCode.defaultMessage());
  }

  public BusinessException(ErrorCode errorCode, String message) {
    this(errorCode, message, null, null, null);
  }

  public BusinessException(ErrorCode errorCode, String message, ErrorMetadata metadata) {
    this(errorCode, message, null, metadata, null);
  }

  public BusinessException(
      ErrorCode errorCode,
      String message,
      ErrorMetadata metadata,
      Map<String, Object> details
  ) {
    this(errorCode, message, null, metadata, details);
  }

  public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
    this(errorCode, message, cause, null, null);
  }

  public BusinessException(ErrorCode errorCode, String message, Throwable cause, ErrorMetadata metadata) {
    this(errorCode, message, cause, metadata, null);
  }

  public BusinessException(
      ErrorCode errorCode,
      String message,
      Throwable cause,
      ErrorMetadata metadata,
      Map<String, Object> details
  ) {
    super(message, cause);
    this.errorCode = errorCode;
    this.metadata = metadata;
    this.details = details == null || details.isEmpty() ? null : Map.copyOf(details);
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public ErrorMetadata getMetadata() {
    return metadata;
  }

  public Map<String, Object> getDetails() {
    return details;
  }
}
