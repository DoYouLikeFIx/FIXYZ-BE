package com.fix.corebank.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class CorebankOpenApiCompatibilityTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldDocumentMappedExternalErrorMetadataInCommittedContract() throws Exception {
    JsonNode contract = objectMapper.readTree(Files.readString(openApiContract()));

    JsonNode paths = contract.path("paths");
    JsonNode apiErrorSchema = contract.path("components").path("schemas").path("ApiErrorResponse");
    JsonNode orderResponse = contract.path("components").path("schemas").path("ApiResponseInternalOrderResponse");
    JsonNode portfolioResponse = contract.path("components").path("schemas").path("ApiResponseInternalPortfolioResponse");
    JsonNode accountPositionResponse = contract.path("components").path("schemas")
        .path("ApiResponseInternalAccountPositionResponse");
    JsonNode accountPositionSchema = contract.path("components").path("schemas")
        .path("InternalAccountPositionResponse");
    JsonNode accountPositionListResponse = contract.path("components").path("schemas")
        .path("ApiResponseListInternalAccountPositionResponse");
    JsonNode accountOrderHistoryResponse = contract.path("components").path("schemas")
        .path("ApiResponseInternalAccountOrderHistoryResponse");
    JsonNode accountOrderHistorySchema = contract.path("components").path("schemas")
        .path("InternalAccountOrderHistoryResponse");
    JsonNode accountOrderHistoryItemSchema = resolveRefSchema(
        contract,
        accountOrderHistorySchema.path("properties").path("content").path("items").path("$ref").asText()
    );
    JsonNode internalOrderSchema = contract.path("components").path("schemas").path("InternalOrderResponse");
    JsonNode requeryOperation = paths.path("/internal/v1/orders/{clOrdId}/requery").path("get");
    JsonNode requeryParameters = requeryOperation.path("parameters");
    JsonNode attemptCountParameter = parameterByName(requeryParameters, "attemptCount");
    JsonNode positionsOperation = paths.path("/internal/v1/accounts/{accountId}/positions").path("get");
    JsonNode positionsListOperation = paths.path("/internal/v1/accounts/{accountId}/positions/list").path("get");
    JsonNode summaryOperation = paths.path("/internal/v1/accounts/{accountId}/summary").path("get");

    assertThat(fieldNames(paths))
        .contains(
            "/internal/v1/orders",
            "/internal/v1/orders/{clOrdId}/requery",
            "/internal/v1/portfolio",
            "/internal/v1/accounts/{accountId}/positions",
            "/internal/v1/accounts/{accountId}/positions/list",
            "/internal/v1/accounts/{accountId}/summary",
            "/internal/v1/accounts/{accountId}/orders"
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

    assertThat(orderResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(portfolioResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountPositionResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountPositionListResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountOrderHistoryResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(fieldNames(accountPositionSchema.path("properties")))
        .contains("quantity", "availableQuantity", "availableQty", "balance", "availableBalance", "asOf");
    assertThat(positionsOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseInternalAccountPositionResponse");
    assertThat(summaryOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseInternalAccountPositionResponse");
    assertThat(positionsListOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseListInternalAccountPositionResponse");
    assertThat(accountPositionListResponse.path("properties").path("data").path("type").asText())
        .isEqualTo("array");
    assertThat(accountPositionListResponse.path("properties").path("data").path("items").path("$ref").asText())
        .isEqualTo("#/components/schemas/InternalAccountPositionResponse");
    assertThat(fieldNames(accountOrderHistorySchema.path("properties")))
        .contains("content", "totalElements", "totalPages", "number", "size");
    assertThat(fieldNames(accountOrderHistoryItemSchema.path("properties")))
        .contains("symbol", "symbolName", "side", "qty", "unitPrice", "totalAmount", "status", "clOrdId", "createdAt");
    assertThat(fieldNames(internalOrderSchema.path("properties")))
        .contains("message", "retriable", "escalationRequired", "attemptCount", "maxRetryCount", "externalSyncStatus");
    assertThat(requeryParameters.isArray()).isTrue();
    assertThat(attemptCountParameter.path("name").asText()).isEqualTo("attemptCount");
    assertThat(attemptCountParameter.path("in").asText()).isEqualTo("query");
    assertThat(attemptCountParameter.path("required").asBoolean()).isFalse();
    assertThat(attemptCountParameter.path("schema").path("minimum").asInt())
        .isEqualTo(1);
    assertThat(attemptCountParameter.path("schema").path("default").asInt())
        .isEqualTo(1);
    assertThat(parameterByName(requeryParameters, "request").isMissingNode()).isTrue();
  }

  private Path openApiContract() {
    Path current = Path.of(System.getProperty("user.dir"));
    for (int i = 0; i < 3 && current != null; i++) {
      Path candidate = current.resolve("contracts").resolve("openapi").resolve("corebank-service.json");
      if (Files.exists(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    return Path.of(System.getProperty("user.dir"), "contracts", "openapi", "corebank-service.json");
  }

  private Set<String> fieldNames(JsonNode node) {
    Set<String> names = new TreeSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private JsonNode parameterByName(JsonNode parameters, String name) {
    for (JsonNode parameter : parameters) {
      if (name.equals(parameter.path("name").asText())) {
        return parameter;
      }
    }
    return MissingNode.getInstance();
  }

  private JsonNode resolveRefSchema(JsonNode contract, String ref) {
    if (ref == null || !ref.startsWith("#/components/schemas/")) {
      return objectMapper.createObjectNode();
    }
    String schemaName = ref.substring("#/components/schemas/".length());
    return contract.path("components").path("schemas").path(schemaName);
  }
}
