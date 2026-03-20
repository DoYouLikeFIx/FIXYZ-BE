package com.fix.corebank.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.validation.ContractPatterns;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.TraceparentSupport;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
public class FepClient {

  private static final String FEP_ORDERS_PATH = "/fep/v1/orders";
  private static final String FEP_ORDER_STATUS_PATH = "/fep/v1/orders/{clOrdId}/status";
  private static final String FEP_ORDER_REPLAY_PATH = "/fep/v1/orders/{clOrdId}/replay";

  private final RestClient restClient;
  private final String internalSecret;
  private final ObjectMapper objectMapper;

  @Autowired
  public FepClient(
      RestClient.Builder restClientBuilder,
      @Value("${fep.gateway.base-url:http://localhost:8083}") String fepGatewayBaseUrl,
      @Value("${internal.secret:local-internal-secret}") String internalSecret
  ) {
    this(restClientBuilder
        .requestFactory(new SimpleClientHttpRequestFactory())
        .baseUrl(fepGatewayBaseUrl)
        .build(), internalSecret);
  }

  protected FepClient(RestClient restClient, String internalSecret) {
    this(restClient, internalSecret, new ObjectMapper());
  }

  private FepClient(RestClient restClient, String internalSecret, ObjectMapper objectMapper) {
    this.restClient = restClient;
    this.internalSecret = internalSecret;
    this.objectMapper = objectMapper;
  }

  @CircuitBreaker(name = "fep-submit", fallbackMethod = "submitOrderFallback")
  public FepOrderResult submitOrder(FepOutboundOrderPayload payload, String correlationId) {
    try {
      String traceparent = TraceparentSupport.currentOrGenerate();
      FepGatewayEnvelope<FepGatewayOrderResponse> response = restClient.post()
          .uri(FEP_ORDERS_PATH)
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, correlationId)
          .header(CommonHeaders.TRACEPARENT, traceparent)
          .header(CommonHeaders.X_CL_ORD_ID, payload.clOrdId())
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });

      FepGatewayOrderResponse responseBody = extractBody(response, "submit");
      return FepOrderResult.fromSubmitResponse(responseBody, payload.clOrdId());
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  @CircuitBreaker(name = "fep-status", fallbackMethod = "queryOrderStatusFallback")
  public FepOrderResult queryOrderStatus(String clOrdId, String correlationId) {
    if (!ContractPatterns.isUuidV4(clOrdId)) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "clOrdId must be a UUID v4");
    }
    try {
      String traceparent = TraceparentSupport.currentOrGenerate();
      FepGatewayEnvelope<FepGatewayOrderResponse> response = restClient.get()
          .uri(FEP_ORDER_STATUS_PATH, clOrdId)
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, correlationId)
          .header(CommonHeaders.TRACEPARENT, traceparent)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });

      FepGatewayOrderResponse responseBody = extractBody(response, "status");
      return FepOrderResult.fromStatusResponse(responseBody, clOrdId);
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  @CircuitBreaker(name = "fep-replay", fallbackMethod = "replayOrderFallback")
  public FepReplayResult replayOrder(FepReplayPayload payload, String correlationId) {
    if (!ContractPatterns.isUuidV4(payload.clOrdId())) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "clOrdId must be a UUID v4");
    }
    try {
      String traceparent = TraceparentSupport.currentOrGenerate();
      FepGatewayEnvelope<FepGatewayReplayResponse> response = restClient.post()
          .uri(FEP_ORDER_REPLAY_PATH, payload.clOrdId())
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, correlationId)
          .header(CommonHeaders.TRACEPARENT, traceparent)
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });

      FepGatewayReplayResponse responseBody = extractReplayBody(response);
      return FepReplayResult.fromResponse(responseBody, payload.clOrdId());
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  @SuppressWarnings("unused")
  private FepOrderResult submitOrderFallback(FepOutboundOrderPayload payload, String correlationId, Throwable throwable) {
    throw translateFailure(throwable);
  }

  @SuppressWarnings("unused")
  private FepOrderResult queryOrderStatusFallback(String clOrdId, String correlationId, Throwable throwable) {
    throw translateFailure(throwable);
  }

  @SuppressWarnings("unused")
  private FepReplayResult replayOrderFallback(FepReplayPayload payload, String correlationId, Throwable throwable) {
    throw translateFailure(throwable);
  }

  private FepGatewayOrderResponse extractBody(FepGatewayEnvelope<FepGatewayOrderResponse> response, String operationName) {
    if (response == null || !response.success() || response.data() == null) {
      throw new BusinessException(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          "empty " + operationName + " response from fep gateway"
      );
    }
    return response.data();
  }

  private FepGatewayReplayResponse extractReplayBody(FepGatewayEnvelope<FepGatewayReplayResponse> response) {
    if (response == null || !response.success() || response.data() == null) {
      throw new BusinessException(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          "empty replay response from fep gateway"
      );
    }
    return response.data();
  }

  private BusinessException translateFailure(Throwable throwable) {
    if (throwable instanceof BusinessException businessException) {
      return businessException;
    }
    if (throwable instanceof CallNotPermittedException) {
      return new BusinessException(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          ErrorCode.FEP_GATEWAY_UNAVAILABLE.defaultMessage(),
          throwable,
          new ErrorMetadata("error.fep.unavailable", "CIRCUIT_OPEN")
      );
    }
    if (throwable instanceof RestClientResponseException restClientResponseException) {
      GatewayApiErrorResponse errorResponse = parseError(restClientResponseException.getResponseBodyAsString());
      BusinessException translatedError = translateGatewayError(errorResponse, restClientResponseException);
      if (translatedError != null) {
        return translatedError;
      }
      if (restClientResponseException.getStatusCode().value() == 401) {
        return new BusinessException(
            ErrorCode.AUTH_REQUIRED,
            defaultIfBlank(
                errorResponse == null ? null : errorResponse.message(),
                ErrorCode.AUTH_REQUIRED.defaultMessage()
            ),
            restClientResponseException,
            errorResponse == null ? null : errorResponse.metadata()
        );
      }
      if (restClientResponseException.getStatusCode().value() == 504) {
        return new BusinessException(
            ErrorCode.FEP_GATEWAY_TIMEOUT,
            ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage(),
            restClientResponseException,
            new ErrorMetadata("error.fep.timeout", "TIMEOUT")
        );
      }
      return new BusinessException(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          ErrorCode.FEP_GATEWAY_UNAVAILABLE.defaultMessage(),
          restClientResponseException
      );
    }
    if (throwable instanceof RestClientException) {
      return new BusinessException(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          ErrorCode.FEP_GATEWAY_UNAVAILABLE.defaultMessage(),
          throwable
      );
    }
    return new BusinessException(
        ErrorCode.FEP_GATEWAY_UNAVAILABLE,
        ErrorCode.FEP_GATEWAY_UNAVAILABLE.defaultMessage(),
        throwable
    );
  }

  private BusinessException translateGatewayError(
      GatewayApiErrorResponse errorResponse,
      RestClientResponseException restClientResponseException
  ) {
    if (errorResponse == null) {
      return null;
    }
    if (FepExternalErrorTaxonomy.isMappedExternalRc(errorResponse.externalRc())) {
      return FepExternalErrorTaxonomy.toException(errorResponse.externalRc(), restClientResponseException);
    }
    if (FepExternalErrorTaxonomy.isMappedExternalRc(errorResponse.normalizedCode())) {
      return FepExternalErrorTaxonomy.toException(errorResponse.normalizedCode(), restClientResponseException);
    }
    if (errorResponse.normalizedCode() != null && !errorResponse.normalizedCode().isBlank()) {
      BusinessException normalizedError = ErrorCode.fromCode(errorResponse.normalizedCode())
          .map(errorCode -> new BusinessException(
              errorCode,
              defaultIfBlank(errorResponse.message(), errorCode.defaultMessage()),
              restClientResponseException,
              errorResponse.metadata()
          ))
          .orElse(null);
      if (normalizedError != null) {
        if (normalizedError.getErrorCode() == ErrorCode.FEP_UNKNOWN_EXTERNAL
            && FepExternalErrorTaxonomy.isExternalRc(errorResponse.externalRc())) {
          return FepExternalErrorTaxonomy.toException(errorResponse.externalRc(), restClientResponseException);
        }
        return normalizedError;
      }
    }
    if (FepExternalErrorTaxonomy.isExternalRc(errorResponse.externalRc())) {
      return FepExternalErrorTaxonomy.toException(errorResponse.externalRc(), restClientResponseException);
    }
    return null;
  }

  private GatewayApiErrorResponse parseError(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return null;
    }
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      String topLevelCode = text(root, "code");
      String normalizedCode = firstNonBlank(
          text(root, "error", "code"),
          FepExternalErrorTaxonomy.isExternalRc(topLevelCode) ? null : topLevelCode
      );
      String externalRc = firstExternalRc(text(root, "rc"), topLevelCode, text(root, "error", "rc"));
      String message = firstNonBlank(text(root, "error", "message"), text(root, "message"));
      String userMessageKey = firstNonBlank(text(root, "error", "userMessageKey"), text(root, "userMessageKey"));
      String operatorCode = firstNonBlank(
          text(root, "error", "operatorCode"),
          text(root, "operatorCode"),
          text(root, "error", "rcDescription"),
          text(root, "rcDescription")
      );

      if (externalRc == null && normalizedCode == null && message == null) {
        return null;
      }
      return new GatewayApiErrorResponse(
          externalRc,
          normalizedCode,
          message,
          toMetadata(userMessageKey, operatorCode)
      );
    } catch (Exception ignored) {
      return null;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GatewayApiErrorResponse(
      String externalRc,
      String normalizedCode,
      String message,
      ErrorMetadata metadata
  ) {
  }

  private ErrorMetadata toMetadata(String userMessageKey, String operatorCode) {
    if ((userMessageKey == null || userMessageKey.isBlank())
        && (operatorCode == null || operatorCode.isBlank())) {
      return null;
    }
    return new ErrorMetadata(userMessageKey, operatorCode);
  }

  private String text(JsonNode root, String... path) {
    JsonNode current = root;
    for (String segment : path) {
      if (current == null) {
        return null;
      }
      current = current.path(segment);
      if (current.isMissingNode() || current.isNull()) {
        return null;
      }
    }
    String value = current.asText(null);
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }

  private String firstExternalRc(String... candidates) {
    for (String candidate : candidates) {
      if (FepExternalErrorTaxonomy.isExternalRc(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return null;
  }

  private String defaultIfBlank(String value, String defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value;
  }
}
