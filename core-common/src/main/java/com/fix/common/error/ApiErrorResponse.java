package com.fix.common.error;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
public class ApiErrorResponse {

  private final String code;
  private final String message;
  private final String path;
  private final String correlationId;
  private final String userMessageKey;
  private final String operatorCode;
  private final Map<String, Object> additionalProperties;
  private final Instant timestamp;

  private ApiErrorResponse(
      String code,
      String message,
      String path,
      String correlationId,
      String userMessageKey,
      String operatorCode,
      Map<String, Object> additionalProperties,
      Instant timestamp
  ) {
    this.code = code;
    this.message = message;
    this.path = path;
    this.correlationId = correlationId;
    this.userMessageKey = userMessageKey;
    this.operatorCode = operatorCode;
    this.additionalProperties = additionalProperties == null ? Map.of() : Map.copyOf(additionalProperties);
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
        metadata != null ? metadata.additionalProperties() : Map.of(),
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

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  public Instant getTimestamp() {
    return timestamp;
  }
}
