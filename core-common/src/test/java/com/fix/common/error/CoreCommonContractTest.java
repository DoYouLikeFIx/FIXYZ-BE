package com.fix.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreCommonContractTest {

  @Test
  void shouldBuildSuccessApiResponse() {
    ApiResponse<String> response = ApiResponse.success("ok");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData()).isEqualTo("ok");
    assertThat(response.getError()).isNull();
    assertThat(response.getTimestamp()).isNotNull();
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

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getError().getCode()).isEqualTo("VALIDATION_001");
    assertThat(response.getError().getMessage()).isEqualTo("Validation failed");
    assertThat(response.getError().getPath()).isEqualTo("/api/v1/test");
    assertThat(response.getError().getCorrelationId()).isEqualTo("corr-1");
    assertThat(response.getError().getUserMessageKey()).isNull();
    assertThat(response.getError().getOperatorCode()).isNull();
    assertThat(response.getTimestamp()).isNotNull();
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

    assertThat(error.getCode()).isEqualTo("FEP-002");
    assertThat(error.getUserMessageKey()).isEqualTo("error.fep.timeout");
    assertThat(error.getOperatorCode()).isEqualTo("TIMEOUT");
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

    assertThat(error.getAdditionalProperties().get("enrollUrl")).isEqualTo("/settings/totp/enroll");
    assertThat(error.getAdditionalProperties()).containsKey("enrollUrl");
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

    assertThat(error.getDetails()).isNotNull();
    assertThat(error.getDetails().get("requestedQty")).isEqualTo("50.0000");
    assertThat(error.getDetails().get("remainingLimit")).isEqualTo("30.0000");
    assertThat(error.getUserMessageKey()).isEqualTo("error.order.daily_limit_exceeded");
    assertThat(error.getOperatorCode()).isEqualTo("DAILY_LIMIT_EXCEEDED");
  }

  @Test
  void shouldResolveContractErrorCodes() {
    assertThat(ErrorCode.fromCode("AUTH-005").orElseThrow())
        .isEqualTo(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP);
    assertThat(ErrorCode.fromCode("CORE-901").orElseThrow())
        .isEqualTo(ErrorCode.CORE_DEPENDENCY_TIMEOUT);
    assertThat(ErrorCode.fromCode("CORE-902").orElseThrow())
        .isEqualTo(ErrorCode.CORE_DEPENDENCY_UNAVAILABLE);
    assertThat(ErrorCode.fromCode("ORD-012").orElseThrow())
        .isEqualTo(ErrorCode.ORD_ACCOUNT_STATUS_BLOCKED);
    assertThat(ErrorCode.fromCode("ORD-002").orElseThrow())
        .isEqualTo(ErrorCode.ORD_DAILY_SELL_LIMIT_EXCEEDED);
    assertThat(ErrorCode.fromCode("ORD-003").orElseThrow())
        .isEqualTo(ErrorCode.ORD_INSUFFICIENT_POSITION);
    assertThat(ErrorCode.fromCode("AUTH-026").orElseThrow())
        .isEqualTo(ErrorCode.AUTH_MFA_REBIND_CURRENT_PASSWORD_MISMATCH);
  }
}
