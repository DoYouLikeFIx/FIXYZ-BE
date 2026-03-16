package com.fix.fepsimulator.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class FepSimulatorOpenApiCompatibilityTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldDocumentSharedErrorMetadataInCommittedContract() throws Exception {
    JsonNode contract = objectMapper.readTree(Files.readString(openApiContract()));

    JsonNode paths = contract.path("paths");
    JsonNode putRulePath = paths.path("/fep-internal/rules").path("put");
    JsonNode apiErrorSchema = contract.path("components").path("schemas").path("ApiErrorResponse");
    JsonNode upsertRuleResponse = contract.path("components").path("schemas").path("ApiResponseSimulatorRuleResponse");
    JsonNode listRuleResponse = contract.path("components").path("schemas").path("ApiResponseSimulatorRuleListResponse");
    JsonNode clearRuleResponse = contract.path("components").path("schemas").path("ApiResponseSimulatorRuleClearResponse");

    assertThat(fieldNames(paths))
      .contains("/fep-internal/rules");

    assertThat(putRulePath.path("requestBody").path("content").path("application/json")
      .path("schema").path("$ref").asText())
      .isEqualTo("#/components/schemas/SimulatorRuleUpsertRequest");
    assertThat(putRulePath.path("parameters").isArray()).isFalse();

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
}
