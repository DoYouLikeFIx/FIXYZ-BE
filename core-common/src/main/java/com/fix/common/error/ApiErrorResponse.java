package com.fix.common.error;

import java.time.Instant;

public record ApiErrorResponse(
    String code,
    String message,
    String path,
    Instant timestamp
) {
  public static ApiErrorResponse from(ErrorCode code, String message, String path) {
    return new ApiErrorResponse(code.name(), message, path, Instant.now());
  }
}
