package com.fix.fepgateway.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
public abstract class FepGatewayContainersIntegrationTestBase {

  private static final boolean LOCAL_REUSE_ENABLED =
      Boolean.parseBoolean(System.getProperty("testcontainers.reuse", "false"))
          && !"true".equalsIgnoreCase(System.getenv().getOrDefault("CI", "false"));

  @Container
  protected static final MySQLContainer<?> MYSQL_CONTAINER =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
          .withDatabaseName("fep_gateway_it")
          .withUsername("test")
          .withPassword("test")
          .withReuse(LOCAL_REUSE_ENABLED);

  @Container
  protected static final GenericContainer<?> REDIS_CONTAINER =
      new GenericContainer<>(DockerImageName.parse("redis:7.2.7-alpine"))
          .withExposedPorts(6379)
          .withReuse(LOCAL_REUSE_ENABLED);

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
    registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL_CONTAINER::getDriverClassName);
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
  }

  protected WireMockServer wireMockServer() {
    return new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
  }
}
