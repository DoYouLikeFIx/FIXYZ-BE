package com.fix.channel.config;

import com.fix.common.openapi.OpenApiDocumentationSupport;
import com.fix.common.openapi.OpenApiSummarySupport;
import io.swagger.v3.oas.models.Operation;
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
    };
  }

  private void applyCommonFailureResponses(Operation operation) {
    OpenApiDocumentationSupport.addErrorResponseIfMissing(operation, "400", "Bad Request");
    OpenApiDocumentationSupport.addErrorResponseIfMissing(operation, "500", "Internal Server Error");
    OpenApiDocumentationSupport.normalizeDocumentedErrorResponses(operation);
  }
}
