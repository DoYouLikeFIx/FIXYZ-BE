package com.fix.corebank.client;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.validation.ContractPatterns;
import com.fix.common.web.CommonHeaders;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

@Component
public class FepClient {

  private static final String FEP_ORDERS_PATH = "/fep/v1/orders";
  private static final String FEP_ORDER_STATUS_PATH = "/fep/v1/orders/{clOrdId}/status";

  private final RestClient restClient;
  private final String internalSecret;

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

  FepClient(RestClient restClient, String internalSecret) {
    this.restClient = restClient;
    this.internalSecret = internalSecret;
  }

  @CircuitBreaker(name = "fep", fallbackMethod = "submitOrderFallback")
  public FepOrderResult submitOrder(FepOutboundOrderPayload payload, String correlationId) {
    FepGatewayEnvelope<FepGatewayOrderResponse> response = restClient.post()
        .uri(FEP_ORDERS_PATH)
        .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
        .header(CommonHeaders.X_CORRELATION_ID, correlationId)
        .header(CommonHeaders.X_CL_ORD_ID, payload.clOrdId())
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {
        });

    FepGatewayOrderResponse responseBody = extractBody(response, "submit");
    return FepOrderResult.fromSubmitResponse(responseBody, payload.clOrdId());
  }

  @CircuitBreaker(name = "fep", fallbackMethod = "queryOrderStatusFallback")
  public FepOrderResult queryOrderStatus(String clOrdId, String correlationId) {
    if (!ContractPatterns.isUuidV4(clOrdId)) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "clOrdId must be a UUID v4");
    }
    FepGatewayEnvelope<FepGatewayOrderResponse> response = restClient.get()
        .uri(FEP_ORDER_STATUS_PATH, clOrdId)
        .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
        .header(CommonHeaders.X_CORRELATION_ID, correlationId)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {
        });

    FepGatewayOrderResponse responseBody = extractBody(response, "status");
    return FepOrderResult.fromStatusResponse(responseBody, clOrdId);
  }

  @SuppressWarnings("unused")
  private FepOrderResult submitOrderFallback(FepOutboundOrderPayload payload, String correlationId, Throwable throwable) {
    throw translateFailure("submit", throwable);
  }

  @SuppressWarnings("unused")
  private FepOrderResult queryOrderStatusFallback(String clOrdId, String correlationId, Throwable throwable) {
    throw translateFailure("status", throwable);
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

  private BusinessException translateFailure(String operationName, Throwable throwable) {
    if (throwable instanceof BusinessException businessException) {
      return businessException;
    }
    if (throwable instanceof RestClientResponseException restClientResponseException) {
      return new BusinessException(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          "fep gateway " + operationName + " failed with HTTP " + restClientResponseException.getStatusCode().value()
      );
    }
    if (throwable instanceof RestClientException) {
      return new BusinessException(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          "fep gateway " + operationName + " request failed"
      );
    }
    return new BusinessException(
        ErrorCode.FEP_GATEWAY_UNAVAILABLE,
        "unexpected fep gateway " + operationName + " failure"
    );
  }
}
