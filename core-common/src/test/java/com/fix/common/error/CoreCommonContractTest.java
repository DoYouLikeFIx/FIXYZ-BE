package com.fix.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
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
    assertNull(response.getError().getUserMessageKey());
    assertNull(response.getError().getOperatorCode());
    assertNotNull(response.getTimestamp());
  }

  @Test
  void shouldIncludeExternalErrorMetadataWhenPresent() {
    ApiErrorResponse error = ApiErrorResponse.from(
        ErrorCode.FEP_GATEWAY_TIMEOUT,
        "",
        "/internal/v1/orders",
        "corr-2",
        new ErrorMetadata("error.fep.timeout", "TIMEOUT")
    );

    assertEquals("FEP-002", error.getCode());
    assertEquals("error.fep.timeout", error.getUserMessageKey());
    assertEquals("TIMEOUT", error.getOperatorCode());
  }

  @Test
  void shouldExposeAdditionalErrorPropertiesWhenPresent() {
    ApiErrorResponse error = ApiErrorResponse.from(
        ErrorCode.AUTH_TOTP_ENROLLMENT_REQUIRED,
        "",
        "/api/v1/auth/otp/verify",
        "corr-3",
        new ErrorMetadata(null, null, java.util.Map.of("enrollUrl", "/settings/totp/enroll"))
    );

    assertEquals("/settings/totp/enroll", error.getAdditionalProperties().get("enrollUrl"));
    assertTrue(error.getAdditionalProperties().containsKey("enrollUrl"));
  }

  @Test
  void shouldIncludeStructuredErrorDetailsWhenPresent() {
    ApiErrorResponse error = ApiErrorResponse.from(
        ErrorCode.ORD_INVALID_REQUEST,
        "Daily sell limit exceeded",
        "/internal/v1/orders",
        "corr-3",
        new ErrorMetadata("error.order.daily_limit_exceeded", "DAILY_LIMIT_EXCEEDED"),
        Map.of(
            "requestedQty", "50.0000",
            "remainingLimit", "30.0000"
        )
    );

    assertNotNull(error.getDetails());
    assertEquals("50.0000", error.getDetails().get("requestedQty"));
    assertEquals("30.0000", error.getDetails().get("remainingLimit"));
    assertEquals("error.order.daily_limit_exceeded", error.getUserMessageKey());
    assertEquals("DAILY_LIMIT_EXCEEDED", error.getOperatorCode());
  }

  @Test
  void shouldResolveStory22AndStory26ContractErrorCodes() {
    assertEquals(
        ErrorCode.AUTH_FORBIDDEN_OWNERSHIP,
        ErrorCode.fromCode("AUTH-005").orElseThrow()
    );
    assertEquals(
        ErrorCode.CORE_DEPENDENCY_TIMEOUT,
        ErrorCode.fromCode("CORE-901").orElseThrow()
    );
    assertEquals(
        ErrorCode.CORE_DEPENDENCY_UNAVAILABLE,
        ErrorCode.fromCode("CORE-902").orElseThrow()
    );
    assertEquals(
        ErrorCode.ORD_ACCOUNT_STATUS_BLOCKED,
        ErrorCode.fromCode("ORD-012").orElseThrow()
    );
    assertEquals(
        ErrorCode.ORD_DAILY_SELL_LIMIT_EXCEEDED,
        ErrorCode.fromCode("ORD-002").orElseThrow()
    );
    assertEquals(
        ErrorCode.ORD_INSUFFICIENT_POSITION,
        ErrorCode.fromCode("ORD-003").orElseThrow()
    );
  }
}
