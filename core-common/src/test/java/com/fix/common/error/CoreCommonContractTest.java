package com.fix.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoreCommonContractTest {

  @Test
  void shouldBuildSuccessApiResponse() {
    ApiResponse<String> response = ApiResponse.success("ok");

    assertTrue(response.isSuccess());
    assertEquals("ok", response.getData());
    assertNull(response.getError());
    assertNotNull(response.getTimestamp());
  }

  @Test
  void shouldBuildErrorResponseWithDefaultMessage() {
    ApiErrorResponse error = ApiErrorResponse.from(
        ErrorCode.VALIDATION_FAILED,
        "",
        "/api/v1/test",
        "corr-1"
    );
    ApiResponse<Void> response = ApiResponse.failure(error);

    assertFalse(response.isSuccess());
    assertEquals("VALIDATION_001", response.getError().getCode());
    assertEquals("Validation failed", response.getError().getMessage());
    assertEquals("/api/v1/test", response.getError().getPath());
    assertEquals("corr-1", response.getError().getCorrelationId());
    assertNotNull(response.getTimestamp());
  }
}
