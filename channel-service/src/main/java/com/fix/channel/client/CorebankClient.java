package com.fix.channel.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.vo.AdminAccountStatusTransitionCommand;
import com.fix.channel.vo.AdminAccountStatusTransitionResult;
import com.fix.channel.vo.AccountPositionQueryCommand;
import com.fix.channel.vo.AccountPositionsQueryCommand;
import com.fix.channel.vo.AccountPositionResult;
import com.fix.channel.vo.AccountSummaryQueryCommand;
import com.fix.channel.vo.AccountOrderHistoryQueryCommand;
import com.fix.channel.vo.AccountOrderHistoryItemResult;
import com.fix.channel.vo.AccountOrderHistoryResult;
import com.fix.channel.vo.OrderExecuteCommand;
import com.fix.channel.vo.OrderExecuteResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.web.CommonHeaders;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Component
public class CorebankClient {

  private static final String COREBANK_ORDERS_PATH = "/internal/v1/orders";
  private static final String COREBANK_ACCOUNT_POSITION_PATH = "/internal/v1/accounts/{accountId}/positions";
  private static final String COREBANK_ACCOUNT_SUMMARY_PATH = "/internal/v1/accounts/{accountId}/summary";
  private static final String COREBANK_ACCOUNT_POSITIONS_PATH = "/internal/v1/accounts/{accountId}/positions/list";
  private static final String COREBANK_ACCOUNT_ORDERS_PATH = "/internal/v1/accounts/{accountId}/orders";
  private static final String COREBANK_ACCOUNT_STATUS_PATH = "/internal/v1/accounts/{accountId}/status";

  private final RestClient restClient;
  private final String internalSecret;
  private final ObjectMapper objectMapper;

  @Autowired
  public CorebankClient(
      RestClient.Builder restClientBuilder,
      @Value("${corebank.internal.base-url:${corebank.base-url:http://localhost:8081}}") String corebankBaseUrl,
      @Value("${corebank.internal.secret:${internal.secret:${INTERNAL_SECRET:local-internal-secret}}}") String internalSecret
  ) {
    this(
        restClientBuilder
            .requestFactory(new HttpComponentsClientHttpRequestFactory())
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
          responseBody.orderQuantity(),
          responseBody.executionResult(),
          responseBody.executedQty(),
          responseBody.leavesQty(),
          responseBody.executedPrice(),
          responseBody.externalOrderId(),
          responseBody.externalSyncStatus(),
          responseBody.executedAt()
      );
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  public AccountPositionResult getAccountPosition(AccountPositionQueryCommand command, String correlationId) {
    try {
      CorebankApiResponse<CorebankAccountPositionResponse> response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(COREBANK_ACCOUNT_POSITION_PATH)
              .queryParam("memberId", command.getMemberId())
              .queryParam("symbol", command.getSymbol())
              .build(command.getAccountId()))
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, correlationId)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });

      return mapAccountPosition(extractBody(response), command.getAccountId(), command.getMemberId(), command.getSymbol());
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  public List<AccountPositionResult> getAccountPositions(
      AccountPositionsQueryCommand command,
      String correlationId
  ) {
    try {
      CorebankApiResponse<List<CorebankAccountPositionResponse>> response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(COREBANK_ACCOUNT_POSITIONS_PATH)
              .queryParam("memberId", command.getMemberId())
              .build(command.getAccountId()))
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, correlationId)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });

      List<CorebankAccountPositionResponse> responseBody = extractBody(response);
      if (responseBody == null) {
        return List.of();
      }

      return responseBody.stream()
          .map((item) -> AccountPositionResult.of(
              firstNonNull(item.accountId(), command.getAccountId()),
              firstNonNull(item.memberId(), command.getMemberId()),
              defaultIfBlank(item.symbol(), ""),
              defaultDecimal(item.quantity(), BigDecimal.ZERO),
              defaultDecimal(firstNonNull(item.availableQuantity(), item.availableQty()), BigDecimal.ZERO),
              defaultDecimal(firstNonNull(item.balance(), item.availableBalance()), BigDecimal.ZERO),
              item.currency(),
              item.asOf()
          ))
          .toList();
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  public AccountPositionResult getAccountSummary(
      AccountSummaryQueryCommand command,
      String correlationId
  ) {
    try {
      CorebankApiResponse<CorebankAccountPositionResponse> response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(COREBANK_ACCOUNT_SUMMARY_PATH)
              .queryParam("memberId", command.getMemberId())
              .build(command.getAccountId()))
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, correlationId)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });

      CorebankAccountPositionResponse responseBody = extractBody(response);
      BigDecimal quantity = defaultDecimal(responseBody.quantity(), BigDecimal.ZERO);
      BigDecimal availableQuantity =
          defaultDecimal(firstNonNull(responseBody.availableQuantity(), responseBody.availableQty()), BigDecimal.ZERO);
      BigDecimal balance = defaultDecimal(firstNonNull(responseBody.balance(), responseBody.availableBalance()), BigDecimal.ZERO);
      return AccountPositionResult.of(
          firstNonNull(responseBody.accountId(), command.getAccountId()),
          firstNonNull(responseBody.memberId(), command.getMemberId()),
          defaultIfBlank(responseBody.symbol(), ""),
          quantity,
          availableQuantity,
          balance,
          responseBody.currency(),
          responseBody.asOf()
      );
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  public AccountOrderHistoryResult getAccountOrderHistory(AccountOrderHistoryQueryCommand command, String correlationId) {
    try {
      CorebankApiResponse<CorebankAccountOrderHistoryResponse> response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(COREBANK_ACCOUNT_ORDERS_PATH)
              .queryParam("memberId", command.getMemberId())
              .queryParam("page", command.getPage())
              .queryParam("size", command.getSize())
              .build(command.getAccountId()))
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, correlationId)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });

      CorebankAccountOrderHistoryResponse responseBody = extractBody(response);
      List<CorebankAccountOrderHistoryItemResponse> coreItems = responseBody.content();
      List<AccountOrderHistoryItemResult> items = coreItems == null
          ? List.of()
          : coreItems.stream().map(this::mapOrderHistoryItem).toList();
      return AccountOrderHistoryResult.of(
          items,
          defaultLong(responseBody.totalElements(), 0L),
          defaultInt(responseBody.totalPages(), 0),
          defaultInt(responseBody.number(), command.getPage()),
          defaultInt(responseBody.size(), command.getSize())
      );
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  public AdminAccountStatusTransitionResult transitionAccountStatus(
      AdminAccountStatusTransitionCommand command,
      String correlationId
  ) {
    try {
      CorebankApiResponse<CorebankAccountStatusTransitionResponse> response = restClient.patch()
          .uri(COREBANK_ACCOUNT_STATUS_PATH, command.getAccountId())
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, correlationId)
          .contentType(MediaType.APPLICATION_JSON)
          .body(new CorebankAccountStatusTransitionRequest(
              command.getMemberId(),
              command.getStatus(),
              command.getReason(),
              command.getActor(),
              command.getContext()
          ))
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });

      CorebankAccountStatusTransitionResponse responseBody = extractBody(response);
      String previousStatus = responseBody.previousStatus();
      String newStatus = responseBody.newStatus();
      if (previousStatus == null || newStatus == null) {
        throw new BusinessException(
            ErrorCode.INTERNAL_ERROR,
            "corebank response missing previousStatus or newStatus"
        );
      }

      return AdminAccountStatusTransitionResult.of(
          defaultLong(responseBody.accountId(), command.getAccountId()),
          defaultLong(responseBody.memberId(), command.getMemberId()),
          previousStatus,
          newStatus,
          responseBody.changed(),
          responseBody.eventId(),
          firstNonNull(responseBody.reason(), command.getReason()),
          firstNonNull(responseBody.actor(), command.getActor()),
          firstNonNull(responseBody.context(), command.getContext()),
          responseBody.asOf()
      );
    } catch (RestClientException ex) {
      throw translateFailure(ex);
    }
  }

  private <T> T extractBody(CorebankApiResponse<T> response) {
    if (response == null || !response.success() || response.data() == null) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "empty corebank response");
    }
    return response.data();
  }

  private AccountPositionResult mapAccountPosition(
      CorebankAccountPositionResponse responseBody,
      Long accountId,
      Long memberId,
      String fallbackSymbol
  ) {
    BigDecimal quantity = defaultDecimal(responseBody.quantity(), BigDecimal.ZERO);
    BigDecimal availableQuantity = defaultDecimal(
        firstNonNull(responseBody.availableQuantity(), responseBody.availableQty()),
        quantity
    );
    BigDecimal balance = defaultDecimal(
        firstNonNull(responseBody.balance(), responseBody.availableBalance()),
        BigDecimal.ZERO
    );

    return AccountPositionResult.of(
        firstNonNull(responseBody.accountId(), accountId),
        firstNonNull(responseBody.memberId(), memberId),
        defaultIfBlank(responseBody.symbol(), fallbackSymbol),
        quantity,
        availableQuantity,
        balance,
        responseBody.currency(),
        responseBody.asOf()
    );
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
                errorResponse.metadata(),
                errorResponse.details()
            ))
            .orElseGet(() -> {
              ErrorCode errorCode = resolveDependencyErrorCode(restClientResponseException);
              return new BusinessException(errorCode, errorCode.defaultMessage(), restClientResponseException);
            });
      }
      ErrorCode errorCode = resolveDependencyErrorCode(restClientResponseException);
      return new BusinessException(errorCode, errorCode.defaultMessage(), restClientResponseException);
    }
    if (throwable instanceof ResourceAccessException resourceAccessException) {
      ErrorCode errorCode = isTimeout(resourceAccessException)
          ? ErrorCode.CORE_DEPENDENCY_TIMEOUT
          : ErrorCode.CORE_DEPENDENCY_UNAVAILABLE;
      return new BusinessException(errorCode, errorCode.defaultMessage(), resourceAccessException);
    }
    if (throwable instanceof RestClientException restClientException) {
      return new BusinessException(
          ErrorCode.CORE_DEPENDENCY_UNAVAILABLE,
          ErrorCode.CORE_DEPENDENCY_UNAVAILABLE.defaultMessage(),
          restClientException
      );
    }
    return new BusinessException(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), throwable);
  }

  private ErrorCode resolveDependencyErrorCode(RestClientResponseException exception) {
    int statusCode = exception.getStatusCode().value();
    if (statusCode == 504) {
      return ErrorCode.CORE_DEPENDENCY_TIMEOUT;
    }
    if (statusCode == 503) {
      return ErrorCode.CORE_DEPENDENCY_UNAVAILABLE;
    }
    return ErrorCode.INTERNAL_ERROR;
  }

  private boolean isTimeout(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SocketTimeoutException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
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

  private <T> T firstNonNull(T value, T fallback) {
    return value != null ? value : fallback;
  }

  private BigDecimal defaultDecimal(BigDecimal value, BigDecimal defaultValue) {
    return value != null ? value : defaultValue;
  }

  private int defaultInt(Integer value, int defaultValue) {
    return value != null ? value : defaultValue;
  }

  private long defaultLong(Long value, long defaultValue) {
    return value != null ? value : defaultValue;
  }

  private AccountOrderHistoryItemResult mapOrderHistoryItem(CorebankAccountOrderHistoryItemResponse item) {
    BigDecimal qty = defaultDecimal(item.qty(), BigDecimal.ZERO);
    BigDecimal unitPrice = defaultDecimal(item.unitPrice(), BigDecimal.ZERO);
    BigDecimal totalAmount = defaultDecimal(item.totalAmount(), qty.multiply(unitPrice));
    String symbol = defaultIfBlank(item.symbol(), item.symbolName());
    String symbolName = defaultIfBlank(item.symbolName(), symbol);
    return AccountOrderHistoryItemResult.of(
        symbol,
        symbolName,
        item.side(),
        qty,
        unitPrice,
        totalAmount,
        item.status(),
        item.clOrdId(),
        item.createdAt()
    );
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
      BigDecimal orderQuantity,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      Instant executedAt
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CorebankAccountPositionResponse(
      Long accountId,
      Long memberId,
      String symbol,
      BigDecimal quantity,
      BigDecimal availableQuantity,
      BigDecimal availableQty,
      BigDecimal balance,
      BigDecimal availableBalance,
      String currency,
      Instant asOf
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CorebankAccountOrderHistoryResponse(
      List<CorebankAccountOrderHistoryItemResponse> content,
      Long totalElements,
      Integer totalPages,
      Integer number,
      Integer size
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CorebankAccountOrderHistoryItemResponse(
      String symbol,
      String symbolName,
      String side,
      BigDecimal qty,
      BigDecimal unitPrice,
      BigDecimal totalAmount,
      String status,
      String clOrdId,
      Instant createdAt
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CorebankAccountStatusTransitionRequest(
      Long memberId,
      String status,
      String reason,
      String actor,
      String context
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CorebankAccountStatusTransitionResponse(
      Long accountId,
      Long memberId,
      String previousStatus,
      String newStatus,
      boolean changed,
      Long eventId,
      String reason,
      String actor,
      String context,
      Instant asOf
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CorebankApiErrorResponse(
      String code,
      String message,
      String userMessageKey,
      String operatorCode,
      Map<String, Object> details
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
