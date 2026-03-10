package com.fix.channel.client;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.web.CommonHeaders;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpCorebankProvisioningClient implements CorebankProvisioningClient {

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
  public void provisionDefaultAccount(Long memberId, String memberNo, String email, String correlationId) {
    CorebankProvisioningRequest requestBody = new CorebankProvisioningRequest(memberId, memberNo, email);
    try {
      CorebankProvisioningEnvelope response = restClient.post()
          .uri("/internal/v1/portfolio")
          .contentType(MediaType.APPLICATION_JSON)
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, resolveCorrelationId(correlationId))
          .body(requestBody)
          .retrieve()
          .body(CorebankProvisioningEnvelope.class);

      if (response == null || !Boolean.TRUE.equals(response.getSuccess()) || response.getData() == null) {
        throw new BusinessException(ErrorCode.CORE_PROVISIONING_UNAVAILABLE, "corebank provisioning failed");
      }
    } catch (RestClientException ex) {
      throw new BusinessException(ErrorCode.CORE_PROVISIONING_UNAVAILABLE, "corebank provisioning failed", ex);
    }
  }

  private String resolveCorrelationId(String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return correlationId;
  }
}
