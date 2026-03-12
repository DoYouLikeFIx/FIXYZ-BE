package com.fix.channel.client;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.web.CommonHeaders;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpCorebankProvisioningClient implements CorebankProvisioningClient {

  private static final String PROVISIONING_PATH = "/internal/v1/portfolio";
  private static final String DEFAULT_ACCOUNT_PATH = "/internal/v1/accounts/default";

  private final RestClient restClient;
  private final String internalSecret;

  public HttpCorebankProvisioningClient(
      RestClient.Builder restClientBuilder,
      @Value("${corebank.internal.base-url:http://localhost:8081}") String corebankBaseUrl,
      @Value("${corebank.internal.secret:${INTERNAL_SECRET:local-internal-secret}}") String internalSecret
  ) {
    this.restClient = restClientBuilder
        .baseUrl(corebankBaseUrl)
        .requestFactory(new SimpleClientHttpRequestFactory())
        .build();
    this.internalSecret = internalSecret;
  }

  @Override
  public CorebankLinkedAccountProfile provisionDefaultAccount(Long memberId, String memberNo, String email, String correlationId) {
    CorebankProvisioningRequest requestBody = new CorebankProvisioningRequest(memberId, memberNo, email);
    try {
      CorebankEnvelope<CorebankLinkedAccountPayload> response = restClient.post()
          .uri(PROVISIONING_PATH)
          .contentType(MediaType.APPLICATION_JSON)
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, resolveCorrelationId(correlationId))
          .body(requestBody)
          .retrieve()
          .body(new org.springframework.core.ParameterizedTypeReference<>() {
          });

      if (response == null || !response.success() || response.data() == null) {
        throw new BusinessException(ErrorCode.CORE_PROVISIONING_UNAVAILABLE, "corebank provisioning failed");
      }
      return response.data().toProfile();
    } catch (RestClientException ex) {
      throw new BusinessException(ErrorCode.CORE_PROVISIONING_UNAVAILABLE, "corebank provisioning failed", ex);
    }
  }

  @Override
  public CorebankLinkedAccountProfile fetchDefaultAccountProfile(Long memberId, String correlationId) {
    try {
      CorebankEnvelope<CorebankLinkedAccountPayload> response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(DEFAULT_ACCOUNT_PATH)
              .queryParam("memberId", memberId)
              .build())
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, resolveCorrelationId(correlationId))
          .retrieve()
          .body(new org.springframework.core.ParameterizedTypeReference<>() {
          });

      if (response == null || !response.success() || response.data() == null) {
        throw new BusinessException(ErrorCode.CORE_PROVISIONING_UNAVAILABLE, "corebank linked account lookup failed");
      }
      return response.data().toProfile();
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        return null;
      }
      throw new BusinessException(ErrorCode.CORE_PROVISIONING_UNAVAILABLE, "corebank linked account lookup failed", ex);
    } catch (RestClientException ex) {
      throw new BusinessException(ErrorCode.CORE_PROVISIONING_UNAVAILABLE, "corebank linked account lookup failed", ex);
    }
  }

  private String resolveCorrelationId(String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return correlationId;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CorebankEnvelope<T>(
      boolean success,
      T data
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CorebankLinkedAccountPayload(
      Long accountId,
      Long memberId,
      String accountNumber
  ) {
    private CorebankLinkedAccountProfile toProfile() {
      return new CorebankLinkedAccountProfile(accountId, memberId, accountNumber);
    }
  }
}
