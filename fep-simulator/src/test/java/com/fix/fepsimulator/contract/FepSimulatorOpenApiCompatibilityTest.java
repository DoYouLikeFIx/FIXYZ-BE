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
    JsonNode apiErrorSchema = contract.path("components").path("schemas").path("ApiErrorResponse");
    JsonNode ruleResponse = contract.path("components").path("schemas").path("ApiResponseSimulatorRuleResponse");
    JsonNode chaosResponse = contract.path("components").path("schemas").path("ApiResponseSimulatorChaosResponse");

    assertThat(fieldNames(paths))
        .contains("/simulator/v1/rules", "/simulator/v1/chaos", "/simulator/v1/rules/{ruleCode}");

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
    assertThat(apiErrorSchema.path("additionalProperties").asBoolean()).isTrue();

    assertThat(ruleResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(chaosResponse.path("properties").path("error").path("$ref").asText())
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
