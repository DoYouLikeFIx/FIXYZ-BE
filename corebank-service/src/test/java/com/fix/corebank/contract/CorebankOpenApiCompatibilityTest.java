package com.fix.corebank.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    assertThat(fieldNames(paths))
        .contains(
            "/internal/v1/orders",
            "/internal/v1/orders/{clOrdId}/requery",
            "/internal/v1/portfolio",
            "/internal/v1/accounts/{accountId}/positions"
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
    assertThat(fieldNames(accountPositionSchema.path("properties")))
        .contains("quantity", "availableQuantity", "availableQty", "balance", "availableBalance", "asOf");
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
}
