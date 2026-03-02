package com.fix.corebank.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;

class CorebankContainersIntegrationTest extends CorebankContainersIntegrationTestBase {

  @Test
  void shouldBootMysqlAndRedisContainers() {
    assertThat(MYSQL_CONTAINER.isRunning()).isTrue();
    assertThat(REDIS_CONTAINER.isRunning()).isTrue();
  }

  @Test
  void shouldResolveWireMockClassesForContractStubCompilation() {
    WireMockServer wireMockServer = wireMockServer();
    wireMockServer.start();
    try {
      assertThat(wireMockServer.baseUrl()).contains("http://localhost");
    } finally {
      wireMockServer.stop();
    }
  }
}
