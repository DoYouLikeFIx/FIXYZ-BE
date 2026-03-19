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
    JsonNode accountStatusResponse = contract.path("components").path("schemas")
        .path("ApiResponseInternalAccountStatusResponse");
    JsonNode accountStatusSchema = contract.path("components").path("schemas")
        .path("InternalAccountStatusResponse");
    JsonNode accountStatusTransitionResponse = contract.path("components").path("schemas")
        .path("ApiResponseInternalAccountStatusTransitionResponse");
    JsonNode accountStatusTransitionSchema = contract.path("components").path("schemas")
        .path("InternalAccountStatusTransitionResponse");
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
    JsonNode ledgerIntegritySummaryResponse = contract.path("components").path("schemas")
        .path("ApiResponseInternalLedgerIntegritySummaryResponse");
    JsonNode ledgerIntegritySummarySchema = contract.path("components").path("schemas")
        .path("InternalLedgerIntegritySummaryResponse");
    JsonNode ledgerIntegrityFailedIdentifierSchema = resolveRefSchema(
        contract,
        ledgerIntegritySummarySchema.path("properties").path("latestFailedIdentifiers").path("items").path("$ref").asText()
    );
    JsonNode internalOrderSchema = contract.path("components").path("schemas").path("InternalOrderResponse");
    JsonNode requeryOperation = paths.path("/internal/v1/orders/{clOrdId}/requery").path("get");
    JsonNode requeryParameters = requeryOperation.path("parameters");
    JsonNode attemptCountParameter = parameterByName(requeryParameters, "attemptCount");
    JsonNode portfolioOperation = paths.path("/internal/v1/portfolio").path("get");
    JsonNode positionsOperation = paths.path("/internal/v1/accounts/{accountId}/positions").path("get");
    JsonNode positionsListOperation = paths.path("/internal/v1/accounts/{accountId}/positions/list").path("get");
    JsonNode summaryOperation = paths.path("/internal/v1/accounts/{accountId}/summary").path("get");
    JsonNode ledgerIntegritySummaryOperation = paths.path("/internal/v1/ledger-integrity/summary").path("get");

    assertThat(fieldNames(paths))
        .contains(
            "/internal/v1/orders",
            "/internal/v1/orders/{clOrdId}/requery",
            "/internal/v1/portfolio",
            "/internal/v1/accounts/{accountId}/positions",
            "/internal/v1/accounts/{accountId}/summary",
            "/internal/v1/accounts/{accountId}/positions/list",
            "/internal/v1/accounts/{accountId}/status",
            "/internal/v1/accounts/default",
            "/internal/v1/accounts/{accountId}/orders",
            "/internal/v1/ledger-integrity/summary"
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
    assertThat(apiErrorSchema.path("additionalProperties").asBoolean()).isTrue();

    assertThat(orderResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(portfolioResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountPositionResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountStatusResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountStatusTransitionResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountPositionListResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(accountOrderHistoryResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(ledgerIntegritySummaryResponse.path("properties").path("error").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(fieldNames(accountPositionSchema.path("properties")))
        .contains("quantity", "availableQuantity", "availableQty", "balance", "availableBalance", "asOf");
    assertThat(fieldNames(accountStatusSchema.path("properties")))
        .contains("accountNumber", "status", "orderEligible", "denialCode", "asOf");
    assertThat(fieldNames(accountStatusTransitionSchema.path("properties")))
        .contains("previousStatus", "newStatus", "changed", "eventId", "reason", "actor", "context", "asOf");
    assertThat(fieldNames(ledgerIntegritySummarySchema.path("properties")))
        .contains(
            "latestRunId",
            "latestRunCheckedAt",
            "latestRunPassed",
            "latestRunAnomalyCount",
            "latestRunSummaryMessage",
            "unresolvedAnomalyCount",
            "repairPendingCount",
            "criticalAnomalyCount",
            "staleLastRun",
            "latestFailedRunId",
            "latestFailedIdentifiers"
        );
    assertThat(ledgerIntegritySummarySchema.path("properties").path("criticalAnomalyCount").path("type").asText())
        .isEqualTo("integer");
    assertThat(ledgerIntegritySummarySchema.path("properties").path("criticalAnomalyCount").path("format").asText())
        .isEqualTo("int64");
    assertThat(ledgerIntegritySummarySchema.path("properties").path("staleLastRun").path("type").asText())
        .isEqualTo("boolean");
    assertThat(requiredFields(ledgerIntegritySummarySchema))
        .doesNotContain(
            "latestRunId",
            "latestRunCheckedAt",
            "latestRunPassed",
            "latestRunAnomalyCount",
            "latestRunSummaryMessage",
            "latestFailedRunId"
        );
    assertThat(fieldNames(ledgerIntegrityFailedIdentifierSchema.path("properties")))
        .contains(
            "anomalyId",
            "anomalyType",
            "accountId",
            "symbol",
            "positionId",
            "executionId",
            "orderId",
            "clOrdId",
            "journalEntryId",
            "ledgerEntryId"
        );
    assertThat(positionsOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseInternalAccountPositionResponse");
    assertThat(summaryOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseInternalAccountPositionResponse");
    assertThat(ledgerIntegritySummaryOperation.path("responses").path("200").path("content").path("*/*").path("schema")
        .path("$ref").asText()).isEqualTo("#/components/schemas/ApiResponseInternalLedgerIntegritySummaryResponse");
    assertThat(positionsListOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseListInternalAccountPositionResponse");
    assertThat(accountPositionListResponse.path("properties").path("data").path("type").asText())
        .isEqualTo("array");
    assertThat(accountPositionListResponse.path("properties").path("data").path("items").path("$ref").asText())
        .isEqualTo("#/components/schemas/InternalAccountPositionResponse");
    assertThat(ledgerIntegritySummaryResponse.path("properties").path("data").path("$ref").asText())
        .isEqualTo("#/components/schemas/InternalLedgerIntegritySummaryResponse");
    assertThat(fieldNames(accountOrderHistorySchema.path("properties")))
        .contains("content", "totalElements", "totalPages", "number", "size");
    assertThat(fieldNames(accountOrderHistoryItemSchema.path("properties")))
        .contains("symbol", "symbolName", "side", "qty", "unitPrice", "totalAmount", "status", "clOrdId", "createdAt");
    assertThat(fieldNames(internalOrderSchema.path("properties")))
        .contains(
            "message",
            "retriable",
            "escalationRequired",
            "attemptCount",
            "maxRetryCount",
            "externalSyncStatus",
            "executionResult",
            "executedQty",
            "leavesQty",
            "executedPrice",
            "externalOrderId",
            "executedAt"
        );
    assertThat(requeryParameters.isArray()).isTrue();
    assertThat(attemptCountParameter.path("name").asText()).isEqualTo("attemptCount");
    assertThat(attemptCountParameter.path("in").asText()).isEqualTo("query");
    assertThat(attemptCountParameter.path("required").asBoolean()).isFalse();
    assertThat(attemptCountParameter.path("schema").path("minimum").asInt())
        .isEqualTo(1);
    assertThat(attemptCountParameter.path("schema").path("default").asInt())
        .isEqualTo(1);
    assertThat(parameterByName(requeryParameters, "request").isMissingNode()).isTrue();
    assertThat(parameterByName(portfolioOperation.path("parameters"), "X-Internal-Secret").isMissingNode()).isFalse();
    assertThat(parameterByName(ledgerIntegritySummaryOperation.path("parameters"), "X-Internal-Secret").isMissingNode())
        .isFalse();
    assertThat(schemaRef(portfolioOperation, "401"))
        .isEqualTo("#/components/schemas/ApiErrorResponse");
    assertThat(portfolioOperation.path("responses").path("401").path("headers").path("X-Correlation-Id").path("schema")
        .path("type").asText()).isEqualTo("string");
    assertThat(portfolioOperation.path("responses").path("401").path("headers").path("traceparent").path("schema")
        .path("type").asText()).isEqualTo("string");
  }

  @Test
  void shouldDocumentLedgerReconciliationContractsInCommittedOpenApi() throws Exception {
    JsonNode contract = objectMapper.readTree(Files.readString(openApiContract()));

    JsonNode paths = contract.path("paths");
    JsonNode components = contract.path("components").path("schemas");
    JsonNode createCaseOperation = paths.path("/internal/v1/ledger-integrity/anomalies/{anomalyId}/cases").path("post");
    JsonNode transitionCaseOperation = paths.path("/internal/v1/ledger-integrity/cases/{caseId}").path("patch");
    JsonNode repairOperation = paths.path("/internal/v1/ledger-integrity/cases/{caseId}/repairs").path("post");
    JsonNode rerunOperation = paths.path("/internal/v1/ledger-integrity/cases/{caseId}/rerun").path("post");
    JsonNode caseCreateRequest = components.path("InternalLedgerReconciliationCaseCreateRequest");
    JsonNode caseTransitionRequest = components.path("InternalLedgerReconciliationCaseTransitionRequest");
    JsonNode repairRequest = components.path("InternalLedgerReconciliationRepairRequest");
    JsonNode rerunRequest = components.path("InternalLedgerReconciliationRerunRequest");
    JsonNode caseResponse = components.path("InternalLedgerReconciliationCaseResponse");
    JsonNode repairResponse = components.path("InternalLedgerReconciliationRepairResponse");
    JsonNode rerunResponse = components.path("InternalLedgerReconciliationRerunResponse");
    JsonNode wrappedCaseResponse = components.path("ApiResponseInternalLedgerReconciliationCaseResponse");
    JsonNode wrappedRepairResponse = components.path("ApiResponseInternalLedgerReconciliationRepairResponse");
    JsonNode wrappedRerunResponse = components.path("ApiResponseInternalLedgerReconciliationRerunResponse");

    assertThat(fieldNames(paths))
        .contains(
            "/internal/v1/ledger-integrity/anomalies/{anomalyId}/cases",
            "/internal/v1/ledger-integrity/cases/{caseId}",
            "/internal/v1/ledger-integrity/cases/{caseId}/repairs",
            "/internal/v1/ledger-integrity/cases/{caseId}/rerun"
        );

    assertThat(createCaseOperation.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/InternalLedgerReconciliationCaseCreateRequest");
    assertThat(transitionCaseOperation.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/InternalLedgerReconciliationCaseTransitionRequest");
    assertThat(repairOperation.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/InternalLedgerReconciliationRepairRequest");
    assertThat(rerunOperation.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/InternalLedgerReconciliationRerunRequest");

    assertThat(createCaseOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseInternalLedgerReconciliationCaseResponse");
    assertThat(repairOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseInternalLedgerReconciliationRepairResponse");
    assertThat(rerunOperation.path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ApiResponseInternalLedgerReconciliationRerunResponse");

    assertThat(fieldNames(caseCreateRequest.path("properties"))).contains("reason", "actor", "context");
    assertThat(fieldNames(caseTransitionRequest.path("properties"))).contains("status", "reason", "actor", "context");
    assertThat(fieldNames(repairRequest.path("properties"))).contains("repairKey", "repairType", "reason", "actor", "context");
    assertThat(fieldNames(rerunRequest.path("properties"))).contains("reason", "actor", "context");

    assertThat(fieldNames(caseResponse.path("properties")))
        .contains(
            "caseId",
            "anomalyId",
            "runId",
            "currentStatus",
            "created",
            "eventId",
            "anomalyType",
            "summaryMessage",
            "clOrdId",
            "ledgerEntryId",
            "asOf"
        );
    assertThat(fieldNames(repairResponse.path("properties")))
        .contains(
            "repairId",
            "caseId",
            "repairKey",
            "repairType",
            "outcome",
            "idempotent",
            "mutated",
            "caseStatus",
            "rerunRunId",
            "rerunCaseStatus",
            "summaryMessage",
            "asOf"
        );
    assertThat(fieldNames(rerunResponse.path("properties")))
        .contains(
            "caseId",
            "previousStatus",
            "currentStatus",
            "changed",
            "eventId",
            "rerunRunId",
            "anomalyStillPresent",
            "reason",
            "actor",
            "context",
            "asOf"
        );

    assertThat(wrappedCaseResponse.path("properties").path("data").path("$ref").asText())
        .isEqualTo("#/components/schemas/InternalLedgerReconciliationCaseResponse");
    assertThat(wrappedRepairResponse.path("properties").path("data").path("$ref").asText())
        .isEqualTo("#/components/schemas/InternalLedgerReconciliationRepairResponse");
    assertThat(wrappedRerunResponse.path("properties").path("data").path("$ref").asText())
        .isEqualTo("#/components/schemas/InternalLedgerReconciliationRerunResponse");
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

  private Set<String> requiredFields(JsonNode schema) {
    Set<String> names = new TreeSet<>();
    JsonNode required = schema.path("required");
    if (required.isArray()) {
      required.forEach(field -> names.add(field.asText()));
    }
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
