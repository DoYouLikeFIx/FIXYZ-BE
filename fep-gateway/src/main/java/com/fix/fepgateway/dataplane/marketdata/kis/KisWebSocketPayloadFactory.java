package com.fix.fepgateway.dataplane.marketdata.kis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import org.springframework.stereotype.Component;

@Component
public class KisWebSocketPayloadFactory {

  private static final String CONTENT_TYPE = "utf-8";
  private static final String SUBSCRIBE = "1";
  private static final String UNSUBSCRIBE = "2";

  private final ObjectMapper objectMapper;

  public KisWebSocketPayloadFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String createSubscribePayload(
      String approvalKey,
      String custtype,
      MarketDataSubscriptionSpec subscriptionSpec
  ) {
    return createPayload(approvalKey, custtype, SUBSCRIBE, subscriptionSpec);
  }

  public String createUnsubscribePayload(
      String approvalKey,
      String custtype,
      MarketDataSubscriptionSpec subscriptionSpec
  ) {
    return createPayload(approvalKey, custtype, UNSUBSCRIBE, subscriptionSpec);
  }

  private String createPayload(
      String approvalKey,
      String custtype,
      String trType,
      MarketDataSubscriptionSpec subscriptionSpec
  ) {
    requireNonBlank(approvalKey, "approvalKey");
    requireNonBlank(custtype, "custtype");
    if (subscriptionSpec == null) {
      throw new IllegalArgumentException("subscriptionSpec must not be null");
    }
    requireNonBlank(subscriptionSpec.trId(), "subscriptionSpec.trId");
    requireNonBlank(subscriptionSpec.trKey(), "subscriptionSpec.trKey");

    try {
      return objectMapper.writeValueAsString(new WebSocketEnvelope(
          new WebSocketHeader(approvalKey, custtype, trType, CONTENT_TYPE),
          new WebSocketBody(new WebSocketInput(subscriptionSpec.trId(), subscriptionSpec.trKey()))
      ));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize KIS websocket payload", exception);
    }
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }

  private record WebSocketEnvelope(WebSocketHeader header, WebSocketBody body) {
  }

  private record WebSocketHeader(
      @JsonProperty("approval_key") String approvalKey,
      String custtype,
      @JsonProperty("tr_type") String trType,
      @JsonProperty("content-type") String contentType
  ) {
  }

  private record WebSocketBody(WebSocketInput input) {
  }

  private record WebSocketInput(
      @JsonProperty("tr_id") String trId,
      @JsonProperty("tr_key") String trKey
  ) {
  }
}
