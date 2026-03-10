package com.fix.fepgateway.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class FepGatewayOpenApiCompatibilityTest {

  private static final String UUID_V4_PATTERN =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

  private static final Set<String> BASELINE_SUBMIT_FIELDS = Set.of(
      "clOrdId",
      "accountId",
      "symbol",
      "securityExchange",
      "side",
      "orderType",
      "qty",
      "currency",
      "referenceId"
  );

  private static final Set<String> STATUS_RESPONSE_FIELDS = Set.of(
      "clOrdId",
      "fepOrderId",
      "execType",
      "ordStatus",
      "executedQty",
      "executedPrice",
      "leavesQty",
      "transactTime",
      "queryTime"
  );

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldKeepFepOrderContractBackwardCompatibleWithinV1() throws Exception {
    JsonNode contract = objectMapper.readTree(Files.readString(openApiContract()));

    JsonNode submitOperation = contract.path("paths").path("/fep/v1/orders").path("post");
    JsonNode statusOperation = contract.path("paths").path("/fep/v1/orders/{clOrdId}/status").path("get");
    JsonNode cancelOperation = contract.path("paths").path("/fep/v1/orders/{clOrdId}/cancel").path("post");
    JsonNode replayOperation = contract.path("paths").path("/fep/v1/orders/{clOrdId}/replay").path("post");
    JsonNode internalStatusOperation = contract.path("paths").path("/fep-internal/v1/orders/{clOrdId}/status").path("post");
    JsonNode submitSchema = contract.path("components").path("schemas").path("FepOrderSubmitRequest");
    JsonNode cancelSchema = contract.path("components").path("schemas").path("FepOrderCancelRequest");
    JsonNode replaySchema = contract.path("components").path("schemas").path("FepOrderReplayRequest");
    JsonNode internalStatusSchema = contract.path("components").path("schemas").path("FepInternalOrderStatusRequest");
    JsonNode statusResponseSchema = contract.path("components").path("schemas").path("FepOrderResponse");
    JsonNode cancelResponseSchema = contract.path("components").path("schemas").path("FepOrderCancelResponse");
    JsonNode replayResponseSchema = contract.path("components").path("schemas").path("FepOrderReplayResponse");

    assertThat(fieldNames(contract.path("paths")))
        .contains("/fep/v1/orders", "/fep/v1/orders/{clOrdId}/status")
        .doesNotContain("/fep/v2/orders");

    assertThat(parameterNames(submitOperation.path("parameters")))
        .contains("X-Internal-Secret", "X-Correlation-Id", "X-ClOrdID");
    assertThat(parameterNames(statusOperation.path("parameters")))
        .contains("X-Internal-Secret", "X-Correlation-Id")
        .doesNotContain("X-ClOrdID");
    assertThat(parameterNames(cancelOperation.path("parameters")))
        .contains("X-Internal-Secret", "X-Correlation-Id")
        .doesNotContain("X-ClOrdID", "request");
    assertThat(parameterNames(replayOperation.path("parameters")))
        .contains("X-Internal-Secret", "X-Correlation-Id")
        .doesNotContain("X-ClOrdID", "request");
    assertThat(parameterNames(internalStatusOperation.path("parameters")))
        .contains("X-Internal-Secret", "X-Correlation-Id", "X-ClOrdID")
        .doesNotContain("request");

    assertThat(schemaRef(submitOperation, "200")).isEqualTo("#/components/schemas/ApiResponseFepOrderResponse");
    assertThat(schemaRef(submitOperation, "401")).isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(schemaRef(submitOperation, "422")).isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(schemaRef(cancelOperation, "200")).isEqualTo("#/components/schemas/ApiResponseFepOrderCancelResponse");
    assertThat(schemaRef(replayOperation, "200")).isEqualTo("#/components/schemas/ApiResponseFepOrderReplayResponse");
    assertThat(schemaRef(internalStatusOperation, "200")).isEqualTo("#/components/schemas/ApiResponseFepOrderResponse");
    assertThat(cancelOperation.path("responses").path("504").path("description").asText()).contains("9004");
    assertThat(replayOperation.path("responses").path("409").path("description").asText()).contains("9009");

    assertThat(submitOperation.path("requestBody").path("required").asBoolean()).isTrue();
    assertThat(cancelOperation.path("requestBody").path("required").asBoolean()).isTrue();
    assertThat(replayOperation.path("requestBody").path("required").asBoolean()).isTrue();
    assertThat(internalStatusOperation.path("requestBody").path("required").asBoolean()).isTrue();

    assertThat(fieldNames(submitSchema.path("properties"))).containsAll(BASELINE_SUBMIT_FIELDS);
    assertThat(requiredFields(submitSchema))
        .contains("accountId", "clOrdId", "referenceId", "currency", "symbol");
    assertThat(submitSchema.path("properties").path("clOrdId").path("pattern").asText()).isEqualTo(UUID_V4_PATTERN);
    assertThat(submitSchema.path("properties").path("accountId").path("maxLength").asInt()).isEqualTo(64);
    assertThat(submitSchema.path("properties").path("accountId").path("minLength").asInt()).isEqualTo(1);
    assertThat(submitSchema.path("properties").path("referenceId").path("maxLength").asInt()).isEqualTo(128);
    assertThat(submitSchema.path("properties").path("referenceId").path("minLength").asInt()).isEqualTo(1);
    assertThat(cancelSchema.path("properties").path("origClOrdId").path("pattern").asText()).isEqualTo(UUID_V4_PATTERN);
    assertThat(replaySchema.path("properties").path("operatorId").path("pattern").asText()).isEqualTo(UUID_V4_PATTERN);
    assertThat(replaySchema.path("properties").path("approvedBy").path("pattern").asText()).isEqualTo(UUID_V4_PATTERN);
    assertThat(replaySchema.path("properties").path("reason").path("minLength").asInt()).isEqualTo(30);
    assertThat(replaySchema.path("properties").path("executionPrice").path("description").asText())
        .contains("VALIDATION-002", "maxVirtualFillDeviationBps");
    assertThat(fieldNames(internalStatusSchema.path("properties")))
        .contains(
            "status",
            "executedQty",
            "executedPrice",
            "recoveryStatus",
            "requeryStatus",
            "requeryExecutedQty",
            "requeryExecutedPrice",
            "cancelFailureMode",
            "referencePrice"
        );

    assertThat(requiredFields(cancelSchema))
        .containsExactlyInAnyOrder("origClOrdId", "symbol", "side", "cancelQty", "reason");
    assertThat(requiredFields(replaySchema))
        .containsExactlyInAnyOrder("manualDecision", "operatorId", "approvedBy", "evidenceRef", "reason");

    assertThat(fieldNames(statusResponseSchema.path("properties"))).containsAll(STATUS_RESPONSE_FIELDS);
    assertThat(fieldNames(cancelResponseSchema.path("properties")))
        .containsExactlyInAnyOrder(
            "origClOrdId",
            "cancelClOrdId",
            "status",
            "executedQty",
            "canceledQty",
            "executedPrice",
            "executedAt",
            "canceledAt"
        );
    assertThat(fieldNames(replayResponseSchema.path("properties")))
        .containsExactlyInAnyOrder(
            "clOrdId",
            "finalStatus",
            "executionResult",
            "executionSource",
            "executedQty",
            "executedPrice",
            "processedBy",
            "processedAt"
        );
  }

  private Path openApiContract() {
    Path current = Path.of(System.getProperty("user.dir"));
    for (int i = 0; i < 3 && current != null; i++) {
      Path candidate = current.resolve("contracts").resolve("openapi").resolve("fep-gateway.json");
      if (Files.exists(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    return Path.of(System.getProperty("user.dir"), "contracts", "openapi", "fep-gateway.json");
  }

  private Set<String> fieldNames(JsonNode node) {
    Set<String> names = new TreeSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private Set<String> parameterNames(JsonNode node) {
    Set<String> names = new TreeSet<>();
    for (JsonNode parameter : node) {
      names.add(parameter.path("name").asText());
    }
    return names;
  }

  private Set<String> requiredFields(JsonNode schema) {
    Set<String> names = new TreeSet<>();
    for (JsonNode field : schema.path("required")) {
      names.add(field.asText());
    }
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
