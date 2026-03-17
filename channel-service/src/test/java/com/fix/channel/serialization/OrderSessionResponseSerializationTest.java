package com.fix.channel.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fix.channel.dto.response.OrderSessionResponse;
import com.fix.channel.vo.OrderSessionResult;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderSessionResponseSerializationTest {

  private final ObjectMapper objectMapper = JsonMapper.builder()
      .addModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .serializationInclusion(JsonInclude.Include.NON_NULL)
      .build();

  @Test
  void shouldRenderDocumentedNullableFieldsForActiveSessionContract() throws Exception {
    OrderSessionResponse response = OrderSessionResponse.from(OrderSessionResult.of(
        "sess-1",
        "cl-1",
        "AUTHED",
        false,
        "TRUSTED_AUTH_SESSION",
        101L,
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-12T01:00:00Z"),
        2745L,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-12T00:00:00Z"),
        Instant.parse("2026-03-12T00:14:15Z"),
        false
    ));

    JsonNode data = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(data.path("status").asText()).isEqualTo("AUTHED");
    assertThat(data.has("expiresAt")).isTrue();
    assertThat(data.has("remainingSeconds")).isTrue();
    assertThat(data.has("executionResult")).isTrue();
    assertThat(data.path("executionResult").isNull()).isTrue();
    assertThat(data.path("executedQty").isNull()).isTrue();
    assertThat(data.path("leavesQty").isNull()).isTrue();
    assertThat(data.path("executedPrice").isNull()).isTrue();
    assertThat(data.path("externalOrderId").isNull()).isTrue();
    assertThat(data.path("failureReason").isNull()).isTrue();
    assertThat(data.path("executedAt").isNull()).isTrue();
    assertThat(data.path("canceledAt").isNull()).isTrue();
  }

  @Test
  void shouldOmitActiveWindowMetadataOutsideActiveStatusesWhileKeepingNullableFieldsExplicit() throws Exception {
    OrderSessionResponse response = OrderSessionResponse.from(OrderSessionResult.of(
        "sess-2",
        "cl-2",
        "COMPLETED",
        true,
        "ELEVATED_ORDER_RISK",
        101L,
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-12T01:00:00Z"),
        120L,
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "FEP-KRX-90001",
        "CONFIRMED",
        null,
        Instant.parse("2026-03-12T00:05:30Z"),
        null,
        Instant.parse("2026-03-12T00:00:00Z"),
        Instant.parse("2026-03-12T00:05:30Z"),
        false
    ));

    JsonNode data = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(data.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(data.has("expiresAt")).isFalse();
    assertThat(data.has("remainingSeconds")).isFalse();
    assertThat(data.path("executionResult").asText()).isEqualTo("FILLED");
    assertThat(data.path("executedQty").decimalValue()).isEqualByComparingTo("10");
    assertThat(data.path("leavesQty").decimalValue()).isEqualByComparingTo("0");
    assertThat(data.path("executedPrice").decimalValue()).isEqualByComparingTo("72000");
    assertThat(data.has("externalOrderId")).isTrue();
    assertThat(data.path("externalOrderId").asText()).isEqualTo("FEP-KRX-90001");
    assertThat(data.path("externalSyncStatus").asText()).isEqualTo("CONFIRMED");
    assertThat(data.path("failureReason").isNull()).isTrue();
    assertThat(data.path("canceledAt").isNull()).isTrue();
  }

  @Test
  void shouldRenderFailedTerminalContractWithoutActiveWindowMetadata() throws Exception {
    OrderSessionResponse response = OrderSessionResponse.from(OrderSessionResult.of(
        "sess-3",
        "cl-3",
        "FAILED",
        true,
        "ELEVATED_ORDER_RISK",
        101L,
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-12T01:00:00Z"),
        15L,
        null,
        null,
        null,
        null,
        null,
        null,
        "OTP_EXCEEDED",
        null,
        null,
        Instant.parse("2026-03-12T00:00:00Z"),
        Instant.parse("2026-03-12T00:07:00Z"),
        false
    ));

    JsonNode data = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(data.path("status").asText()).isEqualTo("FAILED");
    assertThat(data.has("expiresAt")).isFalse();
    assertThat(data.has("remainingSeconds")).isFalse();
    assertThat(data.has("executionResult")).isTrue();
    assertThat(data.path("executionResult").isNull()).isTrue();
    assertThat(data.has("externalOrderId")).isTrue();
    assertThat(data.path("externalOrderId").isNull()).isTrue();
    assertThat(data.path("externalSyncStatus").isNull()).isTrue();
    assertThat(data.path("failureReason").asText()).isEqualTo("OTP_EXCEEDED");
    assertThat(data.path("executedAt").isNull()).isTrue();
    assertThat(data.path("canceledAt").isNull()).isTrue();
  }

  @Test
  void shouldRenderEscalatedTerminalContractWithPreservedExecutionFields() throws Exception {
    OrderSessionResponse response = OrderSessionResponse.from(OrderSessionResult.of(
        "sess-esc",
        "cl-esc",
        "ESCALATED",
        true,
        "ELEVATED_ORDER_RISK",
        101L,
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-12T01:00:00Z"),
        15L,
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "FEP-KRX-90002",
        "FAILED",
        "ESCALATED_MANUAL_REVIEW",
        Instant.parse("2026-03-12T00:06:30Z"),
        null,
        Instant.parse("2026-03-12T00:00:00Z"),
        Instant.parse("2026-03-12T00:07:00Z"),
        false
    ));

    JsonNode data = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(data.path("status").asText()).isEqualTo("ESCALATED");
    assertThat(data.has("expiresAt")).isFalse();
    assertThat(data.has("remainingSeconds")).isFalse();
    assertThat(data.path("executionResult").asText()).isEqualTo("FILLED");
    assertThat(data.path("executedQty").decimalValue()).isEqualByComparingTo("10");
    assertThat(data.path("leavesQty").decimalValue()).isEqualByComparingTo("0");
    assertThat(data.path("executedPrice").decimalValue()).isEqualByComparingTo("72000");
    assertThat(data.path("externalOrderId").asText()).isEqualTo("FEP-KRX-90002");
    assertThat(data.path("externalSyncStatus").asText()).isEqualTo("FAILED");
    assertThat(data.path("failureReason").asText()).isEqualTo("ESCALATED_MANUAL_REVIEW");
    assertThat(data.path("executedAt").asText()).isEqualTo("2026-03-12T00:06:30Z");
    assertThat(data.path("canceledAt").isNull()).isTrue();
  }

  @Test
  void shouldRenderCanceledTerminalContractWithoutFailureReason() throws Exception {
    OrderSessionResponse response = OrderSessionResponse.from(OrderSessionResult.of(
        "sess-4",
        "cl-4",
        "CANCELED",
        true,
        "ELEVATED_ORDER_RISK",
        101L,
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-12T01:00:00Z"),
        15L,
        "PARTIAL_FILL_CANCEL",
        BigDecimal.valueOf(3),
        BigDecimal.valueOf(7),
        BigDecimal.valueOf(72000),
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-12T00:06:00Z"),
        Instant.parse("2026-03-12T00:00:00Z"),
        Instant.parse("2026-03-12T00:06:00Z"),
        false
    ));

    JsonNode data = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(data.path("status").asText()).isEqualTo("CANCELED");
    assertThat(data.has("expiresAt")).isFalse();
    assertThat(data.has("remainingSeconds")).isFalse();
    assertThat(data.path("executionResult").asText()).isEqualTo("PARTIAL_FILL_CANCEL");
    assertThat(data.path("executedQty").decimalValue()).isEqualByComparingTo("3");
    assertThat(data.path("leavesQty").decimalValue()).isEqualByComparingTo("7");
    assertThat(data.path("executedPrice").decimalValue()).isEqualByComparingTo("72000");
    assertThat(data.path("externalSyncStatus").isNull()).isTrue();
    assertThat(data.path("failureReason").isNull()).isTrue();
    assertThat(data.path("canceledAt").asText()).isEqualTo("2026-03-12T00:06:00Z");
  }
}
