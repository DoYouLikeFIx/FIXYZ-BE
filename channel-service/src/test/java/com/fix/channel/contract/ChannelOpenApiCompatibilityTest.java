package com.fix.channel.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class ChannelOpenApiCompatibilityTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldDocumentSharedErrorMetadataInCommittedContract() throws Exception {
    JsonNode contract = objectMapper.readTree(Files.readString(openApiContract()));

    JsonNode paths = contract.path("paths");
    JsonNode apiErrorSchema = contract.path("components").path("schemas").path("ApiErrorResponse");
    JsonNode loginResponse = contract.path("components").path("schemas").path("ApiResponseAuthLoginResponse");
    JsonNode loginSchema = contract.path("components").path("schemas").path("AuthLoginResponse");
    JsonNode otpVerifyResponse = contract.path("components").path("schemas").path("ApiResponseOtpVerifyResponse");
    JsonNode otpVerifySchema = contract.path("components").path("schemas").path("OtpVerifyResponse");
    JsonNode totpEnrollResponse = contract.path("components").path("schemas").path("ApiResponseTotpEnrollResponse");
    JsonNode totpEnrollSchema = contract.path("components").path("schemas").path("TotpEnrollResponse");
    JsonNode totpRebindBootstrapResponse = contract.path("components").path("schemas")
        .path("ApiResponseTotpRebindBootstrapResponse");
    JsonNode totpRebindBootstrapSchema = contract.path("components").path("schemas").path("TotpRebindBootstrapResponse");
    JsonNode mfaRecoveryRebindConfirmResponse = contract.path("components").path("schemas")
        .path("ApiResponseMfaRecoveryRebindConfirmResponse");
    JsonNode mfaRecoveryRebindConfirmSchema = contract.path("components").path("schemas")
        .path("MfaRecoveryRebindConfirmResponse");
    JsonNode totpEnrollRequestSchema = contract.path("components").path("schemas").path("TotpEnrollRequest");
    JsonNode totpConfirmRequestSchema = contract.path("components").path("schemas").path("TotpConfirmRequest");
    JsonNode otpVerifyRequestSchema = contract.path("components").path("schemas").path("OtpVerifyRequest");
    JsonNode memberTotpRebindRequestSchema = contract.path("components").path("schemas").path("MemberTotpRebindRequest");
    JsonNode mfaRecoveryRebindRequestSchema = contract.path("components").path("schemas").path("MfaRecoveryRebindRequest");
    JsonNode mfaRecoveryRebindConfirmRequestSchema = contract.path("components").path("schemas")
        .path("MfaRecoveryRebindConfirmRequest");
    JsonNode authSessionResponse = contract.path("components").path("schemas")
        .path("ApiResponseAuthSessionResponse");
    JsonNode authSessionSchema = contract.path("components").path("schemas").path("AuthSessionResponse");
    JsonNode sessionResponse = contract.path("components").path("schemas").path("ApiResponseOrderSessionResponse");
    JsonNode accountPositionResponse = contract.path("components").path("schemas")
        .path("ApiResponseAccountPositionResponse");
    JsonNode accountPositionSchema = contract.path("components").path("schemas").path("AccountPositionResponse");
    JsonNode orderSessionCreateRequestSchema = contract.path("components").path("schemas").path("OrderSessionCreateRequest");
    JsonNode orderSessionOtpVerifyRequestSchema = contract.path("components").path("schemas")
        .path("OrderSessionOtpVerifyRequest");
    JsonNode orderSessionSchema = contract.path("components").path("schemas").path("OrderSessionResponse");
    JsonNode accountPositionListResponse = contract.path("components").path("schemas")
        .path("ApiResponseListAccountPositionResponse");
    JsonNode accountOrderHistoryResponse = contract.path("components").path("schemas")
        .path("ApiResponseAccountOrderHistoryResponse");
    JsonNode accountOrderHistorySchema = contract.path("components").path("schemas")
        .path("AccountOrderHistoryResponse");
    JsonNode adminStatusTransitionResponse = contract.path("components").path("schemas")
        .path("ApiResponseAdminAccountStatusTransitionResponse");
    JsonNode adminStatusTransitionSchema = contract.path("components").path("schemas")
        .path("AdminAccountStatusTransitionResponse");
    JsonNode accountOrderHistoryItemSchema = resolveRefSchema(
        contract,
        accountOrderHistorySchema.path("properties").path("content").path("items").path("$ref").asText()
    );
    JsonNode positionsOperation = paths.path("/api/v1/accounts/{accountId}/positions").path("get");
    JsonNode positionsListOperation = paths.path("/api/v1/accounts/{accountId}/positions/list").path("get");
    JsonNode summaryOperation = paths.path("/api/v1/accounts/{accountId}/summary").path("get");

    assertThat(fieldNames(paths))
        .contains(
            "/api/v1/auth/session",
            "/api/v1/auth/login",
            "/api/v1/members/me/totp/enroll",
            "/api/v1/members/me/totp/confirm",
            "/api/v1/members/me/totp/rebind",
            "/api/v1/auth/mfa-recovery/rebind",
            "/api/v1/auth/mfa-recovery/rebind/confirm",
            "/api/v1/orders/sessions",
            "/api/v1/orders/sessions/{orderSessionId}",
            "/api/v1/orders/sessions/{orderSessionId}/otp/verify",
            "/api/v1/orders/sessions/{orderSessionId}/execute",
            "/api/v1/orders/sessions/{orderSessionId}/extend",
            "/api/v1/accounts/{accountId}/positions",
            "/api/v1/accounts/{accountId}/summary",
            "/api/v1/accounts/{accountId}/positions/list",
            "/api/v1/accounts/{accountId}/orders",
            "/api/v1/admin/accounts/{accountId}/status"
        )
        .doesNotContain("/api/v1/orders");

    assertThat(fieldNames(apiErrorSchema.path("properties")))
        .contains(
            "code",
            "message",
            "path",
            "correlationId",
            "userMessageKey",
            "operatorCode",
            "details",
            "timestamp"
        );
    assertThat(apiErrorSchema.path("additionalProperties").asBoolean()).isTrue();

    assertThat(loginResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(totpEnrollResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(totpRebindBootstrapResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(mfaRecoveryRebindConfirmResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(otpVerifyResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(authSessionResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(sessionResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountPositionResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountPositionListResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountOrderHistoryResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(adminStatusTransitionResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(fieldNames(authSessionSchema.path("properties")))
        .contains("memberUuid", "username", "email", "name", "role", "totpEnrolled", "accountId", "accountNumber");
    assertThat(fieldNames(loginSchema.path("properties")))
        .contains("loginToken", "nextAction", "totpEnrolled", "expiresAt");
    assertThat(fieldNames(totpEnrollRequestSchema.path("properties")))
        .contains("loginToken");
    assertThat(fieldNames(totpConfirmRequestSchema.path("properties")))
        .contains("loginToken", "enrollmentToken", "otpCode");
    assertThat(fieldNames(otpVerifyRequestSchema.path("properties")))
        .contains("loginToken", "otpCode");
    assertThat(fieldNames(memberTotpRebindRequestSchema.path("properties")))
        .contains("currentPassword");
    assertThat(fieldNames(mfaRecoveryRebindRequestSchema.path("properties")))
        .contains("recoveryProof");
    assertThat(fieldNames(mfaRecoveryRebindConfirmRequestSchema.path("properties")))
        .contains("rebindToken", "enrollmentToken", "otpCode");
    assertThat(fieldNames(totpEnrollSchema.path("properties")))
        .contains("manualEntryKey", "qrUri", "enrollmentToken", "expiresAt");
    assertThat(fieldNames(totpRebindBootstrapSchema.path("properties")))
        .contains("rebindToken", "manualEntryKey", "qrUri", "enrollmentToken", "expiresAt");
    assertThat(fieldNames(mfaRecoveryRebindConfirmSchema.path("properties")))
        .contains("rebindCompleted", "reauthRequired");
    assertThat(fieldNames(otpVerifySchema.path("properties")))
        .contains("verified", "memberUuid", "email", "name", "role", "totpEnrolled", "accountId", "accountNumber", "mfaVerifiedAt");
    assertThat(fieldNames(accountPositionSchema.path("properties")))
        .contains("quantity", "availableQuantity", "availableQty", "balance", "availableBalance", "asOf");
    assertThat(fieldNames(orderSessionCreateRequestSchema.path("properties")))
        .contains("accountId", "symbol", "side", "orderType", "qty", "price");
    assertThat(fieldNames(orderSessionOtpVerifyRequestSchema.path("properties")))
        .contains("otpCode");
    assertThat(fieldNames(orderSessionSchema.path("properties")))
        .contains(
            "orderSessionId",
            "clOrdId",
            "status",
            "challengeRequired",
            "authorizationReason",
            "accountId",
            "symbol",
            "side",
            "orderType",
            "qty",
            "price",
            "quoteSnapshotId",
            "quoteAsOf",
            "quoteSourceMode",
            "preTradePrice",
            "executionResult",
            "executedQty",
            "leavesQty",
            "executedPrice",
            "externalOrderId",
            "externalSyncStatus",
            "failureReason",
            "executedAt",
            "canceledAt",
            "createdAt",
            "updatedAt",
            "expiresAt",
            "remainingSeconds"
        );
    assertThat(parameterNames(paths.path("/api/v1/orders/sessions").path("post").path("parameters")))
        .contains("X-ClOrdID");
    assertThat(paths.path("/api/v1/orders/sessions/{orderSessionId}/otp/verify").path("post")
        .path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/OrderSessionOtpVerifyRequest");
    assertThat(paths.path("/api/v1/auth/otp/verify").path("post").path("requestBody").path("content")
        .path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/OtpVerifyRequest");
    assertThat(paths.path("/api/v1/members/me/totp/enroll").path("post").path("requestBody").path("content")
        .path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/TotpEnrollRequest");
    assertThat(paths.path("/api/v1/members/me/totp/confirm").path("post").path("requestBody").path("content")
        .path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/TotpConfirmRequest");
    assertThat(paths.path("/api/v1/members/me/totp/rebind").path("post").path("requestBody").path("content")
        .path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/MemberTotpRebindRequest");
    assertThat(paths.path("/api/v1/auth/mfa-recovery/rebind").path("post").path("requestBody").path("content")
        .path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/MfaRecoveryRebindRequest");
    assertThat(paths.path("/api/v1/auth/mfa-recovery/rebind/confirm").path("post").path("requestBody").path("content")
        .path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/MfaRecoveryRebindConfirmRequest");
    assertThat(positionsOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseAccountPositionResponse");
    assertThat(summaryOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseAccountPositionResponse");
    assertThat(positionsListOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseListAccountPositionResponse");
    assertThat(accountPositionListResponse.path("properties").path("data").path("type").asText())
        .isEqualTo("array");
    assertThat(accountPositionListResponse.path("properties").path("data").path("items").path("$ref").asText())
        .isEqualTo("#/components/schemas/AccountPositionResponse");
    assertThat(fieldNames(accountOrderHistorySchema.path("properties")))
        .contains("content", "totalElements", "totalPages", "number", "size");
    assertThat(fieldNames(accountOrderHistoryItemSchema.path("properties")))
        .contains("symbol", "symbolName", "side", "qty", "unitPrice", "totalAmount", "status", "clOrdId", "createdAt");
    assertThat(fieldNames(adminStatusTransitionSchema.path("properties")))
        .contains("previousStatus", "newStatus", "changed", "eventId", "reason", "actor", "context", "asOf");
  }

  private Path openApiContract() {
    Path current = Path.of(System.getProperty("user.dir"));
    for (int i = 0; i < 3 && current != null; i++) {
      Path candidate = current.resolve("contracts").resolve("openapi").resolve("channel-service.json");
      if (Files.exists(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    return Path.of(System.getProperty("user.dir"), "contracts", "openapi", "channel-service.json");
  }

  private Set<String> fieldNames(JsonNode node) {
    Set<String> names = new TreeSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private Set<String> parameterNames(JsonNode node) {
    Set<String> names = new TreeSet<>();
    node.forEach(parameter -> names.add(parameter.path("name").asText()));
    return names;
  }

  private JsonNode resolveRefSchema(JsonNode contract, String ref) {
    if (ref == null || !ref.startsWith("#/components/schemas/")) {
      return objectMapper.createObjectNode();
    }
    String schemaName = ref.substring("#/components/schemas/".length());
    return contract.path("components").path("schemas").path(schemaName);
  }
}
