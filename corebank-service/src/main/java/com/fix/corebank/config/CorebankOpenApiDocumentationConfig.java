package com.fix.corebank.config;

import com.fix.common.openapi.OpenApiDocumentationSupport;
import com.fix.common.openapi.OpenApiSummarySupport;
import com.fix.common.web.CommonHeaders;
import io.swagger.v3.oas.models.Operation;
import com.fix.corebank.security.CorebankInternalSecretPaths;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class CorebankOpenApiDocumentationConfig {

  private static final String INTERNAL_SECRET_DESCRIPTION = "Shared secret required for internal service-to-service requests";
  private static final String INTERNAL_SECRET_AUTH_FAILURE_DESCRIPTION =
      "Missing or invalid X-Internal-Secret rejected before controller execution";

  @Bean
  OperationCustomizer corebankOpenApiDocumentationDefaults() {
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
  OpenApiCustomizer corebankOpenApiSecurityDocumentation() {
    return openApi -> {
      if (openApi.getPaths() == null) {
        return;
      }
      openApi.getPaths().forEach((path, pathItem) -> {
        if (!CorebankInternalSecretPaths.requiresInternalSecret(path)) {
          return;
        }
        pathItem.readOperations().forEach(operation -> {
          OpenApiDocumentationSupport.addRequiredStringHeaderIfMissing(
              operation,
              CommonHeaders.X_INTERNAL_SECRET,
              INTERNAL_SECRET_DESCRIPTION
          );
          OpenApiDocumentationSupport.ensureAuthErrorResponse(
              operation,
              "401",
              INTERNAL_SECRET_AUTH_FAILURE_DESCRIPTION
          );
        });
      });
    };
  }

  private void applyCommonFailureResponses(Operation operation) {
    OpenApiDocumentationSupport.addErrorResponseIfMissing(operation, "400", "Bad Request");
    OpenApiDocumentationSupport.addErrorResponseIfMissing(operation, "500", "Internal Server Error");
    OpenApiDocumentationSupport.normalizeDocumentedErrorResponses(operation);
  }
}
