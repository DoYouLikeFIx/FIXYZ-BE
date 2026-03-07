package com.fix.common.error;

import java.time.Instant;

public class ApiErrorResponse {

  private final String code;
  private final String message;
  private final String path;
  private final String correlationId;
  private final Instant timestamp;

  private ApiErrorResponse(String code, String message, String path, String correlationId, Instant timestamp) {
    this.code = code;
    this.message = message;
    this.path = path;
    this.correlationId = correlationId;
    this.timestamp = timestamp;
  }

  public static ApiErrorResponse from(ErrorCode errorCode, String message, String path) {
    return from(errorCode, message, path, null);
  }

  public static ApiErrorResponse from(ErrorCode errorCode, String message, String path, String correlationId) {
    String resolvedMessage = (message == null || message.isBlank()) ? errorCode.defaultMessage() : message;
    return new ApiErrorResponse(errorCode.code(), resolvedMessage, path, correlationId, Instant.now());
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public String getPath() {
    return path;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public Instant getTimestamp() {
    return timestamp;
  }
}
