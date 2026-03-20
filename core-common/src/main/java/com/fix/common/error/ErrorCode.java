package com.fix.common.error;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
  FEP_ACK_TIMEOUT("9004", "Exchange acknowledgement timed out", 504),
  INVALID_SESSION_STATUS("9009", "Replay target is not escalated", 409),
  AUTH_UNAUTHORIZED("AUTH_001", "Unauthorized", 401),
  AUTH_ACCOUNT_LOCKED("AUTH_002", "Account locked", 401),
  AUTH_REQUIRED("AUTH-003", "Authentication required", 401),
  AUTH_FORBIDDEN_OWNERSHIP("AUTH-005", "Forbidden account ownership", 403),
  AUTH_ACCESS_DENIED("AUTH-006", "Access denied.", 403),
  AUTH_TOTP_ENROLLMENT_REQUIRED("AUTH-009", "totp enrollment required", 403),
  AUTH_OTP_INVALID("AUTH-010", "otp code mismatch", 401),
  AUTH_OTP_REPLAYED("AUTH-011", "otp code already used in current window", 401),
  AUTH_RESET_TOKEN_INVALID("AUTH-012", "reset token invalid or expired", 401),
  AUTH_RESET_TOKEN_CONSUMED("AUTH-013", "reset token already consumed", 409),
  AUTH_PASSWORD_RECOVERY_RATE_LIMIT("AUTH-014", "password recovery rate limit exceeded", 429),
  AUTH_PASSWORD_REUSE_FORBIDDEN("AUTH-015", "new password equals current password", 422),
  AUTH_STALE_SESSION("AUTH-016", "stale session after password change", 401),
  AUTH_LOGIN_TOKEN_EXPIRED("AUTH-018", "login token expired or invalid", 410),
  AUTH_MFA_RECOVERY_TOKEN_INVALID("AUTH-019", "mfa recovery proof or rebind token invalid or expired", 401),
  AUTH_MFA_RECOVERY_TOKEN_CONSUMED("AUTH-020", "mfa recovery proof or rebind token already consumed", 409),
  AUTH_MFA_RECOVERY_REQUIRED("AUTH-021", "mfa recovery required", 403),
  AUTH_MFA_REBIND_CURRENT_PASSWORD_MISMATCH("AUTH-026", "current password mismatch", 401),
  CHANNEL_SESSION_NOT_FOUND("CHANNEL_001", "Channel session not found", 404),
  CHANNEL_SESSION_EXPIRED("CHANNEL-001", "Channel session expired", 410),
  CHANNEL_OTP_MISMATCH("CHANNEL-002", "OTP code mismatch", 422),
  CHANNEL_OTP_ATTEMPTS_EXCEEDED("CHANNEL-003", "OTP attempts exceeded", 403),
  CHANNEL_OWNERSHIP_MISMATCH("CHANNEL-006", "Access denied.", 403),
  CHANNEL_ROUTE_NOT_FOUND("CHANNEL-009", "Routing configuration error", 400),
  CURRENT_PASSWORD_MISMATCH("CURRENT_PASSWORD_MISMATCH", "Current password mismatch", 400),
  CORE_RESOURCE_NOT_FOUND("CORE_001", "Resource not found", 404),
  CORE_DEPENDENCY_TIMEOUT("CORE-901", "Core dependency timeout", 504),
  CORE_DEPENDENCY_UNAVAILABLE("CORE-902", "Core dependency unavailable", 503),
  CORE_PROVISIONING_UNAVAILABLE("CORE-001", "Corebank provisioning unavailable", 503),
  CORE_PROVISIONING_FAILED("CORE-004", "Provisioning transaction failed", 500),
  CORE_CONCURRENCY_CONFLICT("CORE-003", "Concurrent modification conflict", 409),
  FEP_ORDER_NOT_FOUND("9008", "FEP order not found", 404),
  ORD_INSUFFICIENT_CASH("ORD-001", "Insufficient cash", 422),
  ORD_DAILY_SELL_LIMIT_EXCEEDED("ORD-002", "Daily sell limit exceeded", 422),
  ORD_INSUFFICIENT_POSITION("ORD-003", "Insufficient position quantity", 422),
  ORD_INVALID_REQUEST("ORD_001", "Invalid order request", 400),
  ORDER_SESSION_NOT_FOUND("ORD-008", "Order session not found", 404),
  ORDER_SESSION_NOT_AUTHORIZED("ORD-009", "Order session is not authorized for execution", 409),
  ORDER_SESSION_EXECUTION_IN_PROGRESS("ORD-010", "Order execution already in progress", 409),
  ORDER_SESSION_TOTP_REQUIRED("ORD-011", "TOTP enrollment required", 403),
  ORD_ACCOUNT_STATUS_BLOCKED("ORD-012", "Account status blocked", 422),
  RATE_LIMIT_EXCEEDED("RATE_001", "Rate limit exceeded", 429),
  CONTRACT_RATE_LIMIT_EXCEEDED("RATE-001", "Rate limit exceeded", 429),
  FEP_GATEWAY_UNAVAILABLE("FEP-001", "Exchange service unavailable", 503),
  FEP_GATEWAY_TIMEOUT("FEP-002", "Exchange connectivity timeout", 504),
  FEP_ORDER_REJECTED("FEP-003", "Exchange rejected order", 400),
  FEP_INVALID_SESSION_STATE("FEP-004", "Invalid FIX session state", 503),
  FEP_UNKNOWN_EXTERNAL("FEP-999", "Unknown external error", 502),
  SYS_RESOURCE_NOT_FOUND("SYS_404", "Resource not found", 404),
  SYS_INTERNAL_ERROR("SYS_500", "Internal server error", 500);

  private final String code;
  private final String defaultMessage;
  private final int httpStatus;
  private static final Map<String, ErrorCode> BY_CODE = Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(ErrorCode::code, Function.identity()));

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

  public static Optional<ErrorCode> fromCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_CODE.get(code));
  }
}
