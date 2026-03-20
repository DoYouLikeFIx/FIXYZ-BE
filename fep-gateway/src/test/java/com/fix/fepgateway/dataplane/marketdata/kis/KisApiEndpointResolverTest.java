package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KisApiEndpointResolverTest {

  @Test
  void shouldResolvePaperAndDemoToVirtualServers() {
    assertThat(KisApiEndpointResolver.resolveRestBaseUrl("paper"))
        .isEqualTo(KisApiEndpointResolver.PAPER_REST_BASE_URL);
    assertThat(KisApiEndpointResolver.resolveRestBaseUrl("demo"))
        .isEqualTo(KisApiEndpointResolver.PAPER_REST_BASE_URL);
    assertThat(KisApiEndpointResolver.resolveWebSocketBaseUrl("paper"))
        .isEqualTo(KisApiEndpointResolver.PAPER_WS_BASE_URL);
  }

  @Test
  void shouldResolveRealToProductionServers() {
    assertThat(KisApiEndpointResolver.resolveRestBaseUrl("real"))
        .isEqualTo(KisApiEndpointResolver.REAL_REST_BASE_URL);
    assertThat(KisApiEndpointResolver.resolveWebSocketBaseUrl("real"))
        .isEqualTo(KisApiEndpointResolver.REAL_WS_BASE_URL);
  }
}
