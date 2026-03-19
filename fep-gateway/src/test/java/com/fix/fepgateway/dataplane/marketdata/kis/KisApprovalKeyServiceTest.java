package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.fepgateway.config.FepMarketDataProperties;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class KisApprovalKeyServiceTest {

  @Test
  void shouldCacheApprovalKeyUntilInvalidated() {
    AtomicInteger issuedCount = new AtomicInteger();
    KisApprovalClient client = new KisApprovalClient(RestClient.builder().baseUrl("http://localhost").build(), properties()) {
      @Override
      public KisApprovalKey issueApprovalKey() {
        int sequence = issuedCount.incrementAndGet();
        return new KisApprovalKey("approval-key-" + sequence, Instant.parse("2026-03-19T04:00:00Z"));
      }
    };

    KisApprovalKeyService service = new KisApprovalKeyService(client);

    KisApprovalKey first = service.currentOrIssue();
    KisApprovalKey second = service.currentOrIssue();
    service.invalidate();
    KisApprovalKey third = service.currentOrIssue();

    assertThat(first.value()).isEqualTo("approval-key-1");
    assertThat(second.value()).isEqualTo("approval-key-1");
    assertThat(third.value()).isEqualTo("approval-key-2");
    assertThat(issuedCount.get()).isEqualTo(2);
  }

  @Test
  void shouldForceReissueWhenRequested() {
    AtomicInteger issuedCount = new AtomicInteger();
    KisApprovalClient client = new KisApprovalClient(RestClient.builder().baseUrl("http://localhost").build(), properties()) {
      @Override
      public KisApprovalKey issueApprovalKey() {
        int sequence = issuedCount.incrementAndGet();
        return new KisApprovalKey("approval-key-" + sequence, Instant.parse("2026-03-19T04:00:00Z"));
      }
    };

    KisApprovalKeyService service = new KisApprovalKeyService(client);

    KisApprovalKey first = service.currentOrIssue();
    KisApprovalKey second = service.reissue();

    assertThat(first.value()).isEqualTo("approval-key-1");
    assertThat(second.value()).isEqualTo("approval-key-2");
    assertThat(issuedCount.get()).isEqualTo(2);
  }

  private FepMarketDataProperties properties() {
    FepMarketDataProperties properties = new FepMarketDataProperties();
    properties.setProvider("KIS");
    properties.setSourceMode("LIVE");
    properties.getKis().setEnv("paper");
    properties.getKis().setAppKey("paper-app-key");
    properties.getKis().setAppSecret("paper-app-secret");
    return properties;
  }
}
