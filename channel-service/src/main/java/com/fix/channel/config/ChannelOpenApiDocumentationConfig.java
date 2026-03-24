package com.fix.channel.config;

import com.fix.common.openapi.OpenApiDocumentationSupport;
import com.fix.common.openapi.OpenApiSummarySupport;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class ChannelOpenApiDocumentationConfig {

  private static final String AUTHENTICATION_FAILURE_DESCRIPTION = "Authentication required or session is no longer valid";
  private static final String ACCESS_DENIED_DESCRIPTION = "Authenticated user lacks the required role";

  @Bean
  OperationCustomizer channelOpenApiDocumentationDefaults() {
    return (operation, handlerMethod) -> {
      applySummary(operation, handlerMethod);
      applyCommonFailureResponses(operation);
      return operation;
    };
  }

  private void applySummary(Operation operation, HandlerMethod handlerMethod) {
    if (operation.getSummary() == null || operation.getSummary().isBlank()) {
      operation.setSummary(OpenApiSummarySupport.fromMethodName(handlerMethod.getMethod().getName()));
    }
  }

  @Bean
  OpenApiCustomizer channelOpenApiSecurityDocumentation() {
    return openApi -> {
      if (openApi.getPaths() == null) {
        return;
      }
      openApi.getPaths().forEach((path, pathItem) -> {
        if (!ChannelSecurityPaths.requiresAuthentication(path)) {
          return;
        }
        pathItem.readOperations().forEach(operation -> {
          OpenApiDocumentationSupport.ensureAuthErrorResponse(
              operation,
              "401",
              AUTHENTICATION_FAILURE_DESCRIPTION
          );
          if (ChannelSecurityPaths.requiresAdminRole(path)) {
            OpenApiDocumentationSupport.ensureAuthErrorResponse(
                operation,
                "403",
                ACCESS_DENIED_DESCRIPTION
            );
          }
        });
      });
      ensureSummaryResponseSchemas(openApi);
      patchSuccessResponseSchema(
          openApi,
          "/api/v1/accounts/{accountId}/summary",
          "#/components/schemas/ApiResponseAccountSummaryResponse"
      );
      requireNonNullableProperty(openApi, "AccountPositionResponse", "valuationStatus");
    };
  }

  private void applyCommonFailureResponses(Operation operation) {
    OpenApiDocumentationSupport.addErrorResponseIfMissing(operation, "400", "Bad Request");
    OpenApiDocumentationSupport.addErrorResponseIfMissing(operation, "500", "Internal Server Error");
    OpenApiDocumentationSupport.normalizeDocumentedErrorResponses(operation);
  }

  private void ensureSummaryResponseSchemas(OpenAPI openApi) {
    io.swagger.v3.oas.models.Components components = openApi.getComponents();
    if (components == null) {
      components = new io.swagger.v3.oas.models.Components();
      openApi.setComponents(components);
    }
    if (components.getSchemas() == null) {
      components.setSchemas(new LinkedHashMap<>());
    }

    components.getSchemas().put(
        "AccountSummaryResponse",
        new ObjectSchema()
            .addProperty("accountId", new IntegerSchema().format("int64"))
            .addProperty("memberId", new IntegerSchema().format("int64"))
            .addProperty("symbol", new io.swagger.v3.oas.models.media.StringSchema())
            .addProperty("quantity", new NumberSchema())
            .addProperty("availableQuantity", new NumberSchema())
            .addProperty("availableQty", new NumberSchema())
            .addProperty("balance", new NumberSchema())
            .addProperty("availableBalance", new NumberSchema())
            .addProperty("currency", new io.swagger.v3.oas.models.media.StringSchema())
            .addProperty("asOf", new DateTimeSchema())
    );
    components.getSchemas().put(
        "ApiResponseAccountSummaryResponse",
        new ObjectSchema()
            .addProperty("success", new BooleanSchema())
            .addProperty("data", new Schema<>().$ref("#/components/schemas/AccountSummaryResponse"))
            .addProperty("error", new Schema<>().$ref(OpenApiDocumentationSupport.API_ERROR_RESPONSE_REF))
            .addProperty("timestamp", new DateTimeSchema())
    );
  }

  private void patchSuccessResponseSchema(OpenAPI openApi, String path, String schemaRef) {
    if (openApi.getPaths() == null || openApi.getPaths().get(path) == null || openApi.getPaths().get(path).getGet() == null) {
      return;
    }
    Operation operation = openApi.getPaths().get(path).getGet();
    io.swagger.v3.oas.models.responses.ApiResponse response = operation.getResponses() == null
        ? null
        : operation.getResponses().get("200");
    if (response == null) {
      return;
    }
    if (response.getContent() == null || response.getContent().isEmpty()) {
      response.setContent(new io.swagger.v3.oas.models.media.Content().addMediaType(
          "*/*",
          new io.swagger.v3.oas.models.media.MediaType().schema(new Schema<>().$ref(schemaRef))
      ));
      return;
    }
    response.getContent().forEach((ignored, mediaType) -> mediaType.setSchema(new Schema<>().$ref(schemaRef)));
  }

  private void requireNonNullableProperty(OpenAPI openApi, String schemaName, String propertyName) {
    if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
      return;
    }
    Schema<?> schema = openApi.getComponents().getSchemas().get(schemaName);
    if (schema == null || schema.getProperties() == null) {
      return;
    }
    Schema<?> propertySchema = (Schema<?>) schema.getProperties().get(propertyName);
    if (propertySchema == null) {
      return;
    }
    propertySchema.setNullable(false);
    List<String> required = schema.getRequired() == null ? new ArrayList<>() : new ArrayList<>(schema.getRequired());
    if (!required.contains(propertyName)) {
      required.add(propertyName);
      schema.setRequired(required);
    }
  }
}
