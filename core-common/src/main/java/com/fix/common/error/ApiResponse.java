package com.fix.common.error;

import java.time.Instant;

public class ApiResponse<T> {

  private final boolean success;
  private final T data;
  private final ApiErrorResponse error;
  private final Instant timestamp;

  public ApiResponse(boolean success, T data, ApiErrorResponse error, Instant timestamp) {
    this.success = success;
    this.data = data;
    this.error = error;
    this.timestamp = timestamp;
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null, Instant.now());
  }

  public static <T> ApiResponse<T> failure(ApiErrorResponse error) {
    return new ApiResponse<>(false, null, error, Instant.now());
  }

  public boolean isSuccess() {
    return success;
  }

  public T getData() {
    return data;
  }

  public ApiErrorResponse getError() {
    return error;
  }

  public Instant getTimestamp() {
    return timestamp;
  }
}
