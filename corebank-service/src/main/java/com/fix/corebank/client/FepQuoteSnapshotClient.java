package com.fix.corebank.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.TraceparentSupport;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FepQuoteSnapshotClient {

  private static final String FEP_QUOTE_SNAPSHOT_LATEST_PATH = "/fep-internal/v1/quotes/snapshots/latest";
  private static final String FEP_QUOTE_SNAPSHOT_LATEST_BATCH_PATH = "/fep-internal/v1/quotes/snapshots/latest/batch";

  private final RestClient restClient;
  private final String internalSecret;
  private final ObjectMapper objectMapper;

  @Autowired
  public FepQuoteSnapshotClient(
      RestClient.Builder restClientBuilder,
      @Value("${fep.gateway.base-url:http://localhost:8083}") String fepGatewayBaseUrl,
      @Value("${internal.secret:local-internal-secret}") String internalSecret
  ) {
    this(restClientBuilder
        .requestFactory(new SimpleClientHttpRequestFactory())
        .baseUrl(fepGatewayBaseUrl)
        .build(), internalSecret);
  }

  protected FepQuoteSnapshotClient(RestClient restClient, String internalSecret) {
    this(restClient, internalSecret, new ObjectMapper());
  }

  private FepQuoteSnapshotClient(RestClient restClient, String internalSecret, ObjectMapper objectMapper) {
    this.restClient = restClient;
    this.internalSecret = internalSecret;
    this.objectMapper = objectMapper;
  }

  @CircuitBreaker(name = "fep-quote-snapshot", fallbackMethod = "queryLatestQuoteSnapshotFallback")
  public FepQuoteSnapshotResult queryLatestQuoteSnapshot(
      String symbol,
      FepQuoteSourceMode quoteSourceMode,
      String correlationId
  ) {
    if (symbol == null || symbol.isBlank()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "symbol is required");
    }
    if (quoteSourceMode == null) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "quoteSourceMode is required");
    }
    try {
      String traceparent = TraceparentSupport.currentOrGenerate();
      FepGatewayEnvelope<FepGatewayQuoteSnapshotResponse> response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(FEP_QUOTE_SNAPSHOT_LATEST_PATH)
              .queryParam("symbol", symbol)
              .queryParam("quoteSourceMode", quoteSourceMode.name())
              .build())
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, correlationId)
          .header(CommonHeaders.TRACEPARENT, traceparent)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });

      FepGatewayQuoteSnapshotResponse responseBody = extractBody(response);
      return FepQuoteSnapshotResult.fromResponse(responseBody);
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  @CircuitBreaker(name = "fep-quote-snapshot", fallbackMethod = "queryLatestQuoteSnapshotsFallback")
  public Map<String, FepQuoteSnapshotResult> queryLatestQuoteSnapshots(
      List<String> symbols,
      FepQuoteSourceMode quoteSourceMode,
      String correlationId
  ) {
    List<String> normalizedSymbols = normalizeSymbols(symbols);
    if (quoteSourceMode == null) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "quoteSourceMode is required");
    }
    try {
      String traceparent = TraceparentSupport.currentOrGenerate();
      FepGatewayEnvelope<List<FepGatewayQuoteSnapshotResponse>> response = restClient.get()
          .uri(uriBuilder -> {
            var builder = uriBuilder
                .path(FEP_QUOTE_SNAPSHOT_LATEST_BATCH_PATH)
                .queryParam("quoteSourceMode", quoteSourceMode.name());
            normalizedSymbols.forEach(symbol -> builder.queryParam("symbol", symbol));
            return builder.build();
          })
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, correlationId)
          .header(CommonHeaders.TRACEPARENT, traceparent)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });

      Map<String, FepQuoteSnapshotResult> snapshotsBySymbol = new LinkedHashMap<>();
      extractBodyList(response).forEach(snapshotResponse -> {
        FepQuoteSnapshotResult snapshot = FepQuoteSnapshotResult.fromResponse(snapshotResponse);
        snapshotsBySymbol.put(snapshot.symbol(), snapshot);
      });

      Map<String, FepQuoteSnapshotResult> orderedSnapshots = new LinkedHashMap<>();
      normalizedSymbols.forEach(symbol -> {
        FepQuoteSnapshotResult snapshot = snapshotsBySymbol.get(symbol);
        if (snapshot != null) {
          orderedSnapshots.put(symbol, snapshot);
        }
      });
      return orderedSnapshots;
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  @SuppressWarnings("unused")
  private FepQuoteSnapshotResult queryLatestQuoteSnapshotFallback(
      String symbol,
      FepQuoteSourceMode quoteSourceMode,
      String correlationId,
      Throwable throwable
  ) {
    throw translateFailure(throwable);
  }

  @SuppressWarnings("unused")
  private Map<String, FepQuoteSnapshotResult> queryLatestQuoteSnapshotsFallback(
      List<String> symbols,
      FepQuoteSourceMode quoteSourceMode,
      String correlationId,
      Throwable throwable
  ) {
    throw translateFailure(throwable);
  }

  private FepGatewayQuoteSnapshotResponse extractBody(FepGatewayEnvelope<FepGatewayQuoteSnapshotResponse> response) {
    if (response == null || !response.success() || response.data() == null) {
      throw new BusinessException(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          "empty quote snapshot response from fep gateway"
      );
    }
    return response.data();
  }

  private List<FepGatewayQuoteSnapshotResponse> extractBodyList(
      FepGatewayEnvelope<List<FepGatewayQuoteSnapshotResponse>> response
  ) {
    if (response == null || !response.success() || response.data() == null) {
      throw new BusinessException(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          "empty quote snapshot batch response from fep gateway"
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
      BusinessException normalizedError = translateNormalizedError(errorResponse, restClientResponseException);
      if (normalizedError != null) {
        return normalizedError;
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
      if (restClientResponseException.getStatusCode().value() == 404) {
        return new BusinessException(
            ErrorCode.NOT_FOUND,
            defaultIfBlank(
                errorResponse == null ? null : errorResponse.message(),
                ErrorCode.NOT_FOUND.defaultMessage()
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

  private BusinessException translateNormalizedError(
      GatewayApiErrorResponse errorResponse,
      RestClientResponseException restClientResponseException
  ) {
    if (errorResponse == null || errorResponse.normalizedCode() == null || errorResponse.normalizedCode().isBlank()) {
      return null;
    }
    return ErrorCode.fromCode(errorResponse.normalizedCode())
        .map(errorCode -> new BusinessException(
            errorCode,
            defaultIfBlank(errorResponse.message(), errorCode.defaultMessage()),
            restClientResponseException,
            errorResponse.metadata()
        ))
        .orElse(null);
  }

  private GatewayApiErrorResponse parseError(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return null;
    }
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      String normalizedCode = firstNonBlank(text(root, "error", "code"), text(root, "code"));
      String message = firstNonBlank(text(root, "error", "message"), text(root, "message"));
      String userMessageKey = firstNonBlank(text(root, "error", "userMessageKey"), text(root, "userMessageKey"));
      String operatorCode = firstNonBlank(text(root, "error", "operatorCode"), text(root, "operatorCode"));
      if (normalizedCode == null && message == null) {
        return null;
      }
      return new GatewayApiErrorResponse(
          normalizedCode,
          message,
          toMetadata(userMessageKey, operatorCode)
      );
    } catch (Exception ignored) {
      return null;
    }
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

  private List<String> normalizeSymbols(List<String> symbols) {
    if (symbols == null || symbols.isEmpty()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "symbol is required");
    }
    LinkedHashSet<String> normalizedSymbols = new LinkedHashSet<>();
    for (String symbol : symbols) {
      if (symbol == null || symbol.isBlank()) {
        throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "symbol is required");
      }
      normalizedSymbols.add(symbol);
    }
    return List.copyOf(normalizedSymbols);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GatewayApiErrorResponse(
      String normalizedCode,
      String message,
      ErrorMetadata metadata
  ) {
  }
}
