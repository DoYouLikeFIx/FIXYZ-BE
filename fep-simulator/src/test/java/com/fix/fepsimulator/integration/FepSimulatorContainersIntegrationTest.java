package com.fix.fepsimulator.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.fepsimulator.support.FepSimulatorContainersIntegrationTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;

class FepSimulatorContainersIntegrationTest extends FepSimulatorContainersIntegrationTestBase {

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
