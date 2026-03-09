package com.fix.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

  private final String code;
  private final String message;
  private final String path;
  private final String correlationId;
  private final String userMessageKey;
  private final String operatorCode;
  private final Instant timestamp;

  private ApiErrorResponse(
      String code,
      String message,
      String path,
      String correlationId,
      String userMessageKey,
      String operatorCode,
      Instant timestamp
  ) {
    this.code = code;
    this.message = message;
    this.path = path;
    this.correlationId = correlationId;
    this.userMessageKey = userMessageKey;
    this.operatorCode = operatorCode;
    this.timestamp = timestamp;
  }

  public static ApiErrorResponse from(ErrorCode errorCode, String message, String path) {
    return from(errorCode, message, path, null);
  }

  public static ApiErrorResponse from(ErrorCode errorCode, String message, String path, String correlationId) {
    return from(errorCode, message, path, correlationId, null);
  }

  public static ApiErrorResponse from(
      ErrorCode errorCode,
      String message,
      String path,
      String correlationId,
      ErrorMetadata metadata
  ) {
    String resolvedMessage = (message == null || message.isBlank()) ? errorCode.defaultMessage() : message;
    return new ApiErrorResponse(
        errorCode.code(),
        resolvedMessage,
        path,
        correlationId,
        metadata != null ? metadata.userMessageKey() : null,
        metadata != null ? metadata.operatorCode() : null,
        Instant.now()
    );
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

  public String getUserMessageKey() {
    return userMessageKey;
  }

  public String getOperatorCode() {
    return operatorCode;
  }

  public Instant getTimestamp() {
    return timestamp;
  }
}
