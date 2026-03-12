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
    JsonNode orderResponse = contract.path("components").path("schemas").path("ApiResponseOrderResponse");
    JsonNode sessionResponse = contract.path("components").path("schemas").path("ApiResponseOrderSessionResponse");
    JsonNode accountPositionResponse = contract.path("components").path("schemas")
        .path("ApiResponseAccountPositionResponse");
    JsonNode accountPositionSchema = contract.path("components").path("schemas").path("AccountPositionResponse");
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
            "/api/v1/auth/login",
            "/api/v1/orders",
            "/api/v1/orders/sessions",
            "/api/v1/accounts/{accountId}/positions",
            "/api/v1/accounts/{accountId}/positions/list",
            "/api/v1/accounts/{accountId}/summary",
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
            "details",
            "timestamp"
        );

    assertThat(loginResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(orderResponse.path("properties").path("error").path("$ref").asText())
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
    assertThat(fieldNames(accountPositionSchema.path("properties")))
        .contains("quantity", "availableQuantity", "availableQty", "balance", "availableBalance", "asOf");
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

  private JsonNode resolveRefSchema(JsonNode contract, String ref) {
    if (ref == null || !ref.startsWith("#/components/schemas/")) {
      return objectMapper.createObjectNode();
    }
    String schemaName = ref.substring("#/components/schemas/".length());
    return contract.path("components").path("schemas").path(schemaName);
  }
}
