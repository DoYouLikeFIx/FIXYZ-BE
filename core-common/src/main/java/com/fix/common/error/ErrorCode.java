package com.fix.common.error;

public enum ErrorCode {
  BAD_REQUEST("BAD_REQUEST", "Bad request", 400),
  NOT_FOUND("NOT_FOUND", "Resource not found", 404),
  UNAUTHORIZED("UNAUTHORIZED", "Unauthorized", 401),
  INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", 500),

  VALIDATION_FAILED("VALIDATION_001", "Validation failed", 400),
  AUTH_UNAUTHORIZED("AUTH_001", "Unauthorized", 401),
  CHANNEL_SESSION_NOT_FOUND("CHANNEL_001", "Channel session not found", 404),
  CORE_RESOURCE_NOT_FOUND("CORE_001", "Resource not found", 404),
  ORD_INVALID_REQUEST("ORD_001", "Invalid order request", 400),
  FEP_GATEWAY_UNAVAILABLE("FEP_001", "FEP gateway unavailable", 503),
  SYS_RESOURCE_NOT_FOUND("SYS_404", "Resource not found", 404),
  SYS_INTERNAL_ERROR("SYS_500", "Internal server error", 500);

  private final String code;
  private final String defaultMessage;
  private final int httpStatus;

  ErrorCode(String code, String defaultMessage, int httpStatus) {
    this.code = code;
    this.defaultMessage = defaultMessage;
    this.httpStatus = httpStatus;
  }

  public String code() {
    return code;
  }

  public String defaultMessage() {
    return defaultMessage;
  }

  public int httpStatus() {
    return httpStatus;
  }
}
