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
    JsonNode authSessionResponse = contract.path("components").path("schemas")
        .path("ApiResponseAuthSessionResponse");
    JsonNode authSessionSchema = contract.path("components").path("schemas").path("AuthSessionResponse");
    JsonNode orderResponse = contract.path("components").path("schemas").path("ApiResponseOrderResponse");
    JsonNode sessionResponse = contract.path("components").path("schemas").path("ApiResponseOrderSessionResponse");
    JsonNode accountPositionResponse = contract.path("components").path("schemas")
        .path("ApiResponseAccountPositionResponse");
    JsonNode accountPositionSchema = contract.path("components").path("schemas").path("AccountPositionResponse");
    JsonNode orderSessionCreateRequestSchema = contract.path("components").path("schemas").path("OrderSessionCreateRequest");
    JsonNode orderSessionSchema = contract.path("components").path("schemas").path("OrderSessionResponse");
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

    assertThat(fieldNames(paths))
        .contains(
            "/api/v1/auth/session",
            "/api/v1/auth/login",
            "/api/v1/orders",
            "/api/v1/orders/sessions",
            "/api/v1/orders/sessions/{orderSessionId}",
            "/api/v1/accounts/{accountId}/positions",
            "/api/v1/accounts/{accountId}/summary",
            "/api/v1/accounts/{accountId}/positions/list",
            "/api/v1/accounts/{accountId}/orders",
            "/api/v1/admin/accounts/{accountId}/status"
        );

    assertThat(fieldNames(apiErrorSchema.path("properties")))
        .contains(
            "code",
            "message",
            "path",
            "correlationId",
            "userMessageKey",
            "operatorCode",
            "timestamp"
        );

    assertThat(loginResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(authSessionResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(orderResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(sessionResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountPositionResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountOrderHistoryResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(adminStatusTransitionResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(fieldNames(authSessionSchema.path("properties")))
        .contains("memberUuid", "username", "email", "name", "role", "totpEnrolled", "accountId", "accountNumber");
    assertThat(fieldNames(accountPositionSchema.path("properties")))
        .contains("quantity", "availableQuantity", "availableQty", "balance", "availableBalance", "asOf");
    assertThat(fieldNames(orderSessionCreateRequestSchema.path("properties")))
        .contains("accountId", "symbol", "side", "orderType", "qty", "price");
    assertThat(fieldNames(orderSessionSchema.path("properties")))
        .contains(
            "orderSessionId",
            "clOrdId",
            "accountId",
            "symbol",
            "side",
            "orderType",
            "qty",
            "price",
            "createdAt",
            "updatedAt",
            "expiresAt",
            "remainingSeconds"
        );
    assertThat(parameterNames(paths.path("/api/v1/orders/sessions").path("post").path("parameters")))
        .contains("X-ClOrdID");
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
