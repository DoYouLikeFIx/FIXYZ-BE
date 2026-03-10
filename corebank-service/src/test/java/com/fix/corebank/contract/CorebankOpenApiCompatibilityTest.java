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
    JsonNode internalOrderSchema = contract.path("components").path("schemas").path("InternalOrderResponse");
    JsonNode requeryOperation = paths.path("/internal/v1/orders/{clOrdId}/requery").path("get");
    JsonNode requeryParameters = requeryOperation.path("parameters");
    JsonNode attemptCountParameter = parameterByName(requeryParameters, "attemptCount");

    assertThat(fieldNames(paths))
        .contains("/internal/v1/orders", "/internal/v1/orders/{clOrdId}/requery", "/internal/v1/portfolio");

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
}
