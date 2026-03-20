package com.fix.fepsimulator.contract;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class FepSimulatorOpenApiCompatibilityTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldDocumentSharedErrorMetadataInCommittedContract() throws Exception {
    JsonNode contract = objectMapper.readTree(Files.readString(openApiContract()));

    JsonNode paths = contract.path("paths");
    JsonNode putRulePath = paths.path("/fep-internal/rules").path("put");
    JsonNode getRulePath = paths.path("/fep-internal/rules").path("get");
    JsonNode apiErrorSchema = contract.path("components").path("schemas").path("ApiErrorResponse");
    JsonNode upsertRuleResponse = contract.path("components").path("schemas").path("ApiResponseSimulatorRuleResponse");
    JsonNode listRuleResponse = contract.path("components").path("schemas").path("ApiResponseSimulatorRuleListResponse");
    JsonNode clearRuleResponse = contract.path("components").path("schemas").path("ApiResponseSimulatorRuleClearResponse");

    assertThat(fieldNames(paths))
      .contains("/fep-internal/rules");

    assertThat(putRulePath.path("requestBody").path("content").path("application/json")
      .path("schema").path("$ref").asText())
      .isEqualTo("#/components/schemas/SimulatorRuleUpsertRequest");
    assertThat(parameterNames(putRulePath.path("parameters"))).contains("X-Internal-Secret");
    assertThat(parameterNames(getRulePath.path("parameters"))).contains("X-Internal-Secret");
    assertThat(schemaRef(putRulePath, "401"))
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(schemaRef(getRulePath, "401"))
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(putRulePath.path("responses").path("401").path("headers").path("X-Correlation-Id").path("schema")
        .path("type").asText()).isEqualTo("string");
    assertThat(putRulePath.path("responses").path("401").path("headers").path("traceparent").path("schema")
        .path("type").asText()).isEqualTo("string");
    assertThat(getRulePath.path("responses").path("401").path("headers").path("X-Correlation-Id").path("schema")
        .path("type").asText()).isEqualTo("string");
    assertThat(getRulePath.path("responses").path("401").path("headers").path("traceparent").path("schema")
        .path("type").asText()).isEqualTo("string");

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

    assertThat(upsertRuleResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(listRuleResponse.path("properties").path("error").path("$ref").asText())
      .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(clearRuleResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
  }

  private Path openApiContract() {
    Path current = Path.of(System.getProperty("user.dir"));
    for (int i = 0; i < 3 && current != null; i++) {
      Path candidate = current.resolve("contracts").resolve("openapi").resolve("fep-simulator.json");
      if (Files.exists(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    return Path.of(System.getProperty("user.dir"), "contracts", "openapi", "fep-simulator.json");
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

  private String schemaRef(JsonNode operation, String statusCode) {
    JsonNode content = operation.path("responses").path(statusCode).path("content");
    if (content.has("application/json")) {
      return content.path("application/json").path("schema").path("$ref").asText();
    }
    if (content.fieldNames().hasNext()) {
      return content.path(content.fieldNames().next()).path("schema").path("$ref").asText();
    }
    return "";
  }
}
