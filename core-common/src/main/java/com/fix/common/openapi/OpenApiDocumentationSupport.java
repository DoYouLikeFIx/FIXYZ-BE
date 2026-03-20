package com.fix.common.openapi;

import com.fix.common.web.CommonHeaders;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.List;

public final class OpenApiDocumentationSupport {

  public static final String APPLICATION_JSON = "application/json";
  public static final String API_ERROR_RESPONSE_REF = "#/components/schemas/ApiErrorResponse";

  private static final String CORRELATION_ID_HEADER_DESCRIPTION =
      "Correlation identifier echoed by the server for support and trace lookup";
  private static final String TRACEPARENT_HEADER_DESCRIPTION =
      "W3C trace context returned by the server for end-to-end troubleshooting";

  private OpenApiDocumentationSupport() {
  }

  public static void addErrorResponseIfMissing(Operation operation, String statusCode, String description) {
    ApiResponses responses = ensureResponses(operation);
    if (responses.containsKey(statusCode)) {
      return;
    }

    responses.addApiResponse(statusCode, new ApiResponse().description(description));
    ensureApiErrorResponse(operation, statusCode, description);
  }

  public static void ensureApiErrorResponse(Operation operation, String statusCode, String description) {
    ApiResponse response = ensureResponse(operation, statusCode);
    mergeDescription(response, description);
    ensureJsonApiErrorContent(response);
  }

  public static void ensureAuthErrorResponse(
      Operation operation,
      String statusCode,
      String description
  ) {
    ApiResponse response = ensureResponse(operation, statusCode);
    mergeDescription(response, description);
    ensureJsonApiErrorContent(response);
    ensureTracingHeaders(response);
  }

  public static void normalizeDocumentedErrorResponses(Operation operation) {
    ApiResponses responses = operation.getResponses();
    if (responses == null || responses.isEmpty()) {
      return;
    }

    responses.forEach((statusCode, response) -> {
      if (response == null || !isErrorStatusCode(statusCode)) {
        return;
      }
      ensureJsonApiErrorContent(response);
    });
  }

  public static void addRequiredStringHeaderIfMissing(
      Operation operation,
      String headerName,
      String description
  ) {
    List<Parameter> parameters = operation.getParameters();
    if (parameters == null) {
      parameters = new ArrayList<>();
      operation.setParameters(parameters);
    }

    for (Parameter parameter : parameters) {
      if ("header".equals(parameter.getIn()) && headerName.equals(parameter.getName())) {
        parameter.setRequired(true);
        if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
          parameter.setDescription(description);
        }
        if (parameter.getSchema() == null) {
          parameter.setSchema(new StringSchema());
        }
        return;
      }
    }

    parameters.add(new Parameter()
        .in("header")
        .name(headerName)
        .required(true)
        .description(description)
        .schema(new StringSchema()));
  }

  private static ApiResponses ensureResponses(Operation operation) {
    ApiResponses responses = operation.getResponses();
    if (responses == null) {
      responses = new ApiResponses();
      operation.setResponses(responses);
    }
    return responses;
  }

  private static ApiResponse ensureResponse(Operation operation, String statusCode) {
    ApiResponses responses = ensureResponses(operation);
    ApiResponse response = responses.get(statusCode);
    if (response == null) {
      response = new ApiResponse();
      responses.addApiResponse(statusCode, response);
    }
    return response;
  }

  private static void ensureJsonApiErrorContent(ApiResponse response) {
    Content content = response.getContent();
    if (content == null) {
      content = new Content();
      response.setContent(content);
    }

    MediaType wildcard = content.remove("*/*");
    MediaType json = content.get(APPLICATION_JSON);
    if (json == null) {
      json = wildcard != null ? wildcard : new MediaType();
      content.addMediaType(APPLICATION_JSON, json);
    }

    Schema<?> schema = json.getSchema();
    if (schema == null || schema.get$ref() == null || schema.get$ref().isBlank()) {
      json.setSchema(new Schema<>().$ref(API_ERROR_RESPONSE_REF));
    }
  }

  private static void ensureTracingHeaders(ApiResponse response) {
    if (response.getHeaders() == null) {
      response.setHeaders(new java.util.LinkedHashMap<>());
    }
    response.getHeaders().putIfAbsent(
        CommonHeaders.X_CORRELATION_ID,
        new Header().description(CORRELATION_ID_HEADER_DESCRIPTION).schema(new StringSchema())
    );
    response.getHeaders().putIfAbsent(
        CommonHeaders.TRACEPARENT,
        new Header().description(TRACEPARENT_HEADER_DESCRIPTION).schema(new StringSchema())
    );
  }

  private static void mergeDescription(ApiResponse response, String description) {
    if (description == null || description.isBlank()) {
      return;
    }

    String existing = response.getDescription();
    if (existing == null || existing.isBlank()) {
      response.setDescription(description);
      return;
    }

    if (!existing.contains(description)) {
      response.setDescription(existing + "; " + description);
    }
  }

  private static boolean isErrorStatusCode(String statusCode) {
    if (statusCode == null || statusCode.length() != 3) {
      return false;
    }
    char first = statusCode.charAt(0);
    return (first == '4' || first == '5')
        && Character.isDigit(statusCode.charAt(1))
        && Character.isDigit(statusCode.charAt(2));
  }
}
