package com.fix.fepgateway.dataplane.marketdata.kis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fix.fepgateway.config.FepMarketDataProperties;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KisApprovalClient {

  static final String APPROVAL_PATH = "/oauth2/Approval";
  private static final String GRANT_TYPE = "client_credentials";

  private final RestClient restClient;
  private final FepMarketDataProperties properties;

  @Autowired
  public KisApprovalClient(RestClient.Builder restClientBuilder, FepMarketDataProperties properties) {
    this(
        restClientBuilder
            .requestFactory(new SimpleClientHttpRequestFactory())
            .baseUrl(KisApiEndpointResolver.resolveRestBaseUrl(properties.getKis().getEnv()))
            .build(),
        properties
    );
  }

  protected KisApprovalClient(RestClient restClient, FepMarketDataProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  public KisApprovalKey issueApprovalKey() {
    validateCredentials();

    try {
      ApprovalResponse response = restClient.post()
          .uri(APPROVAL_PATH)
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.TEXT_PLAIN)
          .header("charset", "UTF-8")
          .body(new ApprovalRequest(
              GRANT_TYPE,
              properties.getKis().getAppKey(),
              properties.getKis().getAppSecret()
          ))
          .retrieve()
          .body(ApprovalResponse.class);

      if (response == null || response.approvalKey() == null || response.approvalKey().isBlank()) {
        throw new IllegalStateException("KIS approval response missing approval_key");
      }

      return new KisApprovalKey(response.approvalKey(), Instant.now());
    } catch (RestClientResponseException exception) {
      throw new IllegalStateException(
          "KIS approval request failed with status " + exception.getStatusCode().value(),
          exception
      );
    } catch (RestClientException exception) {
      throw new IllegalStateException("KIS approval request failed", exception);
    }
  }

  private void validateCredentials() {
    if (properties.getKis().getAppKey() == null || properties.getKis().getAppKey().isBlank()) {
      throw new IllegalStateException("KIS app key is missing");
    }
    if (properties.getKis().getAppSecret() == null || properties.getKis().getAppSecret().isBlank()) {
      throw new IllegalStateException("KIS app secret is missing");
    }
  }

  private record ApprovalRequest(
      @JsonProperty("grant_type") String grantType,
      @JsonProperty("appkey") String appKey,
      @JsonProperty("secretkey") String secretKey
  ) {
  }

  private record ApprovalResponse(@JsonProperty("approval_key") String approvalKey) {
  }
}
