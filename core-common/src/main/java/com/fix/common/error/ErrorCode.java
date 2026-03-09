package com.fix.common.error;

public enum ErrorCode {
  BAD_REQUEST("BAD_REQUEST", "Bad request", 400),
  NOT_FOUND("NOT_FOUND", "Resource not found", 404),
  UNAUTHORIZED("UNAUTHORIZED", "Unauthorized", 401),
  INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", 500),

  // Keep externally contracted API codes as-is (separator may vary by legacy/story contract).
  VALIDATION_FAILED("VALIDATION_001", "Validation failed", 400),
  CONTRACT_VALIDATION_FAILED("VALIDATION-001", "Validation failed", 422),
  VIRTUAL_FILL_DEVIATION_EXCEEDED("VALIDATION-002", "Virtual fill deviation exceeded", 422),
  MANUAL_REPLAY_GOVERNANCE_FAILED("VALIDATION-004", "Manual replay governance validation failed", 422),
  CANCEL_REJECTED("9006", "Cancel rejected", 409),
  CANCEL_TIMEOUT("9004", "Cancel request timed out", 504),
  INVALID_SESSION_STATUS("9009", "Replay target is not escalated", 409),
  AUTH_UNAUTHORIZED("AUTH_001", "Unauthorized", 401),
  AUTH_REQUIRED("AUTH-003", "Authentication required", 401),
  CHANNEL_SESSION_NOT_FOUND("CHANNEL_001", "Channel session not found", 404),
  CHANNEL_SESSION_EXPIRED("CHANNEL-001", "Channel session expired", 410),
  CURRENT_PASSWORD_MISMATCH("CURRENT_PASSWORD_MISMATCH", "Current password mismatch", 400),
  CORE_RESOURCE_NOT_FOUND("CORE_001", "Resource not found", 404),
  FEP_ORDER_NOT_FOUND("9008", "FEP order not found", 404),
  ORD_INVALID_REQUEST("ORD_001", "Invalid order request", 400),
  RATE_LIMIT_EXCEEDED("RATE_001", "Rate limit exceeded", 429),
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
