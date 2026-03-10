package com.fix.channel.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.vo.OrderExecuteCommand;
import com.fix.channel.vo.OrderExecuteResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.web.CommonHeaders;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CorebankClient {

  private static final String COREBANK_ORDERS_PATH = "/internal/v1/orders";

  private final RestClient restClient;
  private final String internalSecret;
  private final ObjectMapper objectMapper;

  @Autowired
  public CorebankClient(
      RestClient.Builder restClientBuilder,
      @Value("${corebank.base-url:http://localhost:8081}") String corebankBaseUrl,
      @Value("${internal.secret:local-internal-secret}") String internalSecret
  ) {
    this(
        restClientBuilder
            .requestFactory(new SimpleClientHttpRequestFactory())
            .baseUrl(corebankBaseUrl)
            .build(),
        internalSecret
    );
  }

  protected CorebankClient(RestClient restClient, String internalSecret) {
    this.restClient = restClient;
    this.internalSecret = internalSecret;
    this.objectMapper = new ObjectMapper();
  }

  public OrderExecuteResult executeOrder(OrderExecuteCommand command, String correlationId) {
    try {
      CorebankApiResponse<CorebankOrderResponse> response = restClient.post()
          .uri(COREBANK_ORDERS_PATH)
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, correlationId)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(toFormData(command))
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });

      CorebankOrderResponse responseBody = extractBody(response);
      return OrderExecuteResult.of(
          responseBody.orderId(),
          responseBody.clOrdId(),
          responseBody.status(),
          responseBody.idempotent(),
          responseBody.orderQuantity()
      );
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  private CorebankOrderResponse extractBody(CorebankApiResponse<CorebankOrderResponse> response) {
    if (response == null || !response.success() || response.data() == null) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "empty corebank response");
    }
    return response.data();
  }

  private BusinessException translateFailure(Throwable throwable) {
    if (throwable instanceof BusinessException businessException) {
      return businessException;
    }
    if (throwable instanceof RestClientResponseException restClientResponseException) {
      CorebankApiErrorResponse errorResponse = parseError(restClientResponseException.getResponseBodyAsString());
      if (errorResponse != null && errorResponse.code() != null && !errorResponse.code().isBlank()) {
        return ErrorCode.fromCode(errorResponse.code())
            .map(errorCode -> new BusinessException(
                errorCode,
                defaultIfBlank(errorResponse.message(), errorCode.defaultMessage()),
                restClientResponseException,
                errorResponse.metadata()
            ))
            .orElseGet(() -> new BusinessException(
                ErrorCode.INTERNAL_ERROR,
                defaultIfBlank(errorResponse.message(), ErrorCode.INTERNAL_ERROR.defaultMessage()),
                restClientResponseException,
                errorResponse.metadata()
            ));
      }
    }
    return new BusinessException(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), throwable);
  }

  private CorebankApiErrorResponse parseError(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(responseBody, CorebankApiErrorResponse.class);
    } catch (Exception ignored) {
      return null;
    }
  }

  private MultiValueMap<String, String> toFormData(OrderExecuteCommand command) {
    LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("accountId", String.valueOf(command.getAccountId()));
    formData.add("clOrdId", command.getClOrdId());
    formData.add("symbol", command.getSymbol());
    formData.add("side", command.getSide());
    formData.add("quantity", command.getQuantity().toPlainString());
    formData.add("price", command.getPrice().toPlainString());
    return formData;
  }

  private String defaultIfBlank(String value, String defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CorebankApiResponse<T>(
      boolean success,
      T data
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CorebankOrderResponse(
      Long orderId,
      String clOrdId,
      String status,
      boolean idempotent,
      BigDecimal orderQuantity
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CorebankApiErrorResponse(
      String code,
      String message,
      String userMessageKey,
      String operatorCode
  ) {
    private ErrorMetadata metadata() {
      if ((userMessageKey == null || userMessageKey.isBlank())
          && (operatorCode == null || operatorCode.isBlank())) {
        return null;
      }
      return new ErrorMetadata(userMessageKey, operatorCode);
    }
  }
}
