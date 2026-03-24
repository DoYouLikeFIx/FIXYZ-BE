package com.fix.corebank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.web.CommonHeaders;
import com.fix.corebank.CorebankServiceApplication;
import com.fix.corebank.repository.OrderRepository;
import com.fix.fepgateway.FepGatewayApplication;
import com.fix.fepgateway.dataplane.fix.FixDataPlaneService;
import com.fix.fepgateway.vo.GatewayExecutionOutcome;
import com.fix.fepgateway.vo.GatewayOrderSubmitCommand;
import com.fix.fepsimulator.FepSimulatorApplication;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.URISyntaxException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

@SpringBootTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("story-6-6-fep-runtime")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_simulator_resilience;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "internal.secret=test-secret",
    "resilience4j.circuitbreaker.instances.fep-submit.slidingWindowType=COUNT_BASED",
    "resilience4j.circuitbreaker.instances.fep-submit.slidingWindowSize=3",
    "resilience4j.circuitbreaker.instances.fep-submit.minimumNumberOfCalls=3",
    "resilience4j.circuitbreaker.instances.fep-submit.failureRateThreshold=100",
    "resilience4j.circuitbreaker.instances.fep-submit.waitDurationInOpenState=1s",
    "resilience4j.circuitbreaker.instances.fep-submit.permittedNumberOfCallsInHalfOpenState=1",
    "resilience4j.circuitbreaker.instances.fep-submit.automaticTransitionFromOpenToHalfOpenEnabled=true"
})
class CorebankSimulatorDrivenResilienceIntegrationTest {

  private static final String INTERNAL_SECRET = "test-secret";
  private static final String SIMULATOR_INTERNAL_SECRET = "scenario-secret";
  private static final String SYMBOL = "005930";
  private static final String EXCHANGE = "KRX";
  private static final long ORDER_QTY = 2L;
  private static final long ORDER_PRICE = 70_100L;
  private static final long MATCH_AMOUNT = ORDER_QTY * ORDER_PRICE;
  private static final String CL_ORD_ID_FAIL_1 = "123e4567-e89b-42d3-a456-426614174250";
  private static final String CL_ORD_ID_FAIL_2 = "123e4567-e89b-42d3-a456-426614174251";
  private static final String CL_ORD_ID_FAIL_3 = "123e4567-e89b-42d3-a456-426614174252";
  private static final String CL_ORD_ID_OPEN_CALL = "123e4567-e89b-42d3-a456-426614174253";
  private static final String CL_ORD_ID_RECOVERY = "123e4567-e89b-42d3-a456-426614174254";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final ExternalScenarioFixture EXTERNAL_SERVICES = ExternalScenarioFixture.start();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("fep.gateway.base-url", EXTERNAL_SERVICES::gatewayBaseUrl);
    registry.add("spring.flyway.locations", CorebankSimulatorDrivenResilienceIntegrationTest::corebankFlywayLocations);
    registry.add("spring.flyway.placeholders.seed_member_id", () -> 1);
    registry.add("spring.flyway.placeholders.seed_account_id", () -> 1);
    registry.add("spring.flyway.placeholders.seed_position_id", () -> 1);
    registry.add("spring.flyway.placeholders.seed_execution_id", () -> 1);
  }

  @AfterAll
  static void stopExternalServices() {
    EXTERNAL_SERVICES.stop();
  }

  @BeforeEach
  void setUp() {
    EXTERNAL_SERVICES.reset();
    jdbcTemplate.update("DELETE FROM ledger_entry_refs");
    jdbcTemplate.update("DELETE FROM ledger_entries");
    jdbcTemplate.update("DELETE FROM journal_entries");
    jdbcTemplate.update("DELETE FROM executions");
    jdbcTemplate.update("DELETE FROM orders");
    jdbcTemplate.update("DELETE FROM positions");
    jdbcTemplate.update(
        "UPDATE accounts SET status = 'ACTIVE', cash_balance = 100000000.0000, daily_sell_limit = 500.0000 WHERE id = 1"
    );
    jdbcTemplate.update(
        """
            INSERT INTO positions (account_id, symbol, qty, avg_price, created_at, updated_at, version)
            VALUES (1, '005930', 120.0000, 70000.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """
    );
    circuitBreakerRegistry.circuitBreaker("fep-submit").reset();
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM positions", Integer.class)).isEqualTo(1);
  }

  @Test
  @Tag("epic10-acceptance")
  void e10_005ShouldDriveSubmitBreakerTransitionsFromCanonicalChaosRulesApi() throws Exception {
    EXTERNAL_SERVICES.applyTimeoutRule();

    assertThat(EXTERNAL_SERVICES.listActiveRuleCount()).isEqualTo(1);
    assertThat(EXTERNAL_SERVICES.resolveChaosAction()).isEqualTo("TIMEOUT");

    submitOrderExpectingTimeout(CL_ORD_ID_FAIL_1, "trace-sim-resilience-fail-1");
    submitOrderExpectingTimeout(CL_ORD_ID_FAIL_2, "trace-sim-resilience-fail-2");
    submitOrderExpectingTimeout(CL_ORD_ID_FAIL_3, "trace-sim-resilience-fail-3");

    assertCircuitState("OPEN");
    assertThat(EXTERNAL_SERVICES.submitRequestCount()).isEqualTo(3);

    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, INTERNAL_SECRET)
            .header(CommonHeaders.X_CORRELATION_ID, "trace-sim-resilience-open")
            .param("accountId", "1")
            .param("clOrdId", CL_ORD_ID_OPEN_CALL)
            .param("symbol", SYMBOL)
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("FEP-001"))
        .andExpect(jsonPath("$.operatorCode").value("CIRCUIT_OPEN"))
        .andExpect(jsonPath("$.userMessageKey").value("error.fep.unavailable"));

    assertThat(EXTERNAL_SERVICES.submitRequestCount()).isEqualTo(3);

    assertThat(EXTERNAL_SERVICES.clearRules()).isEqualTo(1);
    assertThat(EXTERNAL_SERVICES.listActiveRuleCount()).isZero();
    assertThat(EXTERNAL_SERVICES.resolveChaosAction()).isEqualTo("NONE");

    waitForBreakerToAllowRecoveryProbe();

    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, INTERNAL_SECRET)
            .header(CommonHeaders.X_CORRELATION_ID, "trace-sim-resilience-recovery")
            .param("accountId", "1")
            .param("clOrdId", CL_ORD_ID_RECOVERY)
            .param("symbol", SYMBOL)
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_RECOVERY));

    assertThat(orderRepository.findByClOrdId(CL_ORD_ID_RECOVERY)).isPresent();
    assertCircuitState("CLOSED");
    assertThat(EXTERNAL_SERVICES.submitRequestCount()).isEqualTo(4);
  }

  private void submitOrderExpectingTimeout(String clOrdId, String correlationId) throws Exception {
    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, INTERNAL_SECRET)
            .header(CommonHeaders.X_CORRELATION_ID, correlationId)
            .param("accountId", "1")
            .param("clOrdId", clOrdId)
            .param("symbol", SYMBOL)
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("FEP-002"))
        .andExpect(jsonPath("$.operatorCode").value("TIMEOUT"))
        .andExpect(jsonPath("$.userMessageKey").value("error.fep.timeout"));
  }

  private void assertCircuitState(String expectedState) {
    long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (System.nanoTime() < deadline) {
      if (expectedState.equals(circuitBreakerRegistry.circuitBreaker("fep-submit").getState().name())) {
        return;
      }
      LockSupport.parkNanos(Duration.ofMillis(25).toNanos());
    }
    assertThat(circuitBreakerRegistry.circuitBreaker("fep-submit").getState().name()).isEqualTo(expectedState);
  }

  private void waitForBreakerToAllowRecoveryProbe() {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      if (!"OPEN".equals(circuitBreakerRegistry.circuitBreaker("fep-submit").getState().name())) {
        return;
      }
      LockSupport.parkNanos(Duration.ofMillis(50).toNanos());
    }
    throw new AssertionError("Circuit breaker did not leave OPEN state before recovery probe");
  }

  private static final class CountingFixDataPlaneService extends FixDataPlaneService {

    private final SubmitCounter submitCounter;

    private CountingFixDataPlaneService(
        RestClient simulatorRestClient,
        boolean chaosProbeEnabled,
        SubmitCounter submitCounter
    ) {
      super(simulatorRestClient, chaosProbeEnabled);
      this.submitCounter = submitCounter;
    }

    @Override
    public GatewayExecutionOutcome sendNewOrder(GatewayOrderSubmitCommand command) {
      submitCounter.increment();
      return super.sendNewOrder(command);
    }
  }

  private static final class SubmitCounter {

    private final AtomicInteger count = new AtomicInteger();

    int get() {
      return count.get();
    }

    void increment() {
      count.incrementAndGet();
    }

    void reset() {
      count.set(0);
    }
  }

  private static final class ExternalScenarioFixture {

    private final ConfigurableApplicationContext simulatorContext;
    private final ConfigurableApplicationContext gatewayContext;
    private final SubmitCounter submitCounter;
    private final JdbcTemplate gatewayJdbcTemplate;

    private ExternalScenarioFixture() {
      simulatorContext = startSimulator();
      gatewayContext = startGateway(simulatorBaseUrl());
      submitCounter = gatewayContext.getBean(SubmitCounter.class);
      gatewayJdbcTemplate = gatewayContext.getBean(JdbcTemplate.class);
    }

    static ExternalScenarioFixture start() {
      return new ExternalScenarioFixture();
    }

    String gatewayBaseUrl() {
      return baseUrl(gatewayContext);
    }

    String simulatorBaseUrl() {
      return baseUrl(simulatorContext);
    }

    int submitRequestCount() {
      return submitCounter.get();
    }

    void reset() {
      try {
        clearRules();
      } catch (Exception ex) {
        throw new IllegalStateException("Failed to clear simulator rules during scenario reset", ex);
      }
      gatewayJdbcTemplate.update("DELETE FROM gateway_order_replays");
      gatewayJdbcTemplate.update("DELETE FROM gateway_order_cancels");
      gatewayJdbcTemplate.update("DELETE FROM gateway_security_events");
      gatewayJdbcTemplate.update("DELETE FROM gateway_orders");
      gatewayJdbcTemplate.update("DELETE FROM gateway_sessions");
      submitCounter.reset();
    }

    void applyTimeoutRule() throws Exception {
      HttpRequest request = HttpRequest.newBuilder(URI.create(simulatorBaseUrl() + "/fep-internal/rules"))
          .header(CommonHeaders.X_INTERNAL_SECRET, SIMULATOR_INTERNAL_SECRET)
          .header("Content-Type", "application/json")
          .PUT(HttpRequest.BodyPublishers.ofString("""
              {
                "action": "TIMEOUT",
                "targetSymbol": "005930",
                "targetExchange": "KRX",
                "ttlSeconds": 60,
                "matchAmount": %d,
                "probability": 1.0
              }
              """.formatted(MATCH_AMOUNT)))
          .build();

      JsonNode response = send(request);
      assertThat(response.path("data").path("action").asText()).isEqualTo("TIMEOUT");
    }

    int listActiveRuleCount() throws Exception {
      HttpRequest request = HttpRequest.newBuilder(URI.create(simulatorBaseUrl() + "/fep-internal/rules"))
          .header(CommonHeaders.X_INTERNAL_SECRET, SIMULATOR_INTERNAL_SECRET)
          .GET()
          .build();

      return send(request).path("data").path("activeRules").size();
    }

    String resolveChaosAction() throws Exception {
      HttpRequest request = HttpRequest.newBuilder(URI.create(
          simulatorBaseUrl() + "/api/v1/ping?symbol=" + SYMBOL + "&exchange=" + EXCHANGE + "&amount=" + MATCH_AMOUNT
      )).GET().build();

      return send(request).path("chaosAction").asText();
    }

    int clearRules() throws Exception {
      HttpRequest request = HttpRequest.newBuilder(URI.create(simulatorBaseUrl() + "/fep-internal/rules"))
          .header(CommonHeaders.X_INTERNAL_SECRET, SIMULATOR_INTERNAL_SECRET)
          .DELETE()
          .build();

      return send(request).path("data").path("clearedCount").asInt();
    }

    void stop() {
      gatewayContext.close();
      simulatorContext.close();
    }

    private ConfigurableApplicationContext startSimulator() {
      return new SpringApplicationBuilder(FepSimulatorApplication.class).run(
          "--server.port=0",
          "--management.server.port=0",
          "--internal.secret=" + SIMULATOR_INTERNAL_SECRET,
          "--spring.application.name=fep-simulator",
          "--spring.datasource.url=" + inMemoryDbUrl("fep_simulator_resilience"),
          "--spring.datasource.driver-class-name=org.h2.Driver",
          "--spring.datasource.username=sa",
          "--spring.datasource.password=",
          "--spring.jpa.hibernate.ddl-auto=validate",
          "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
          "--spring.flyway.enabled=true",
          "--spring.flyway.validate-on-migrate=true",
          "--spring.flyway.locations=" + flywayLocations(FepSimulatorApplication.class)
      );
    }

    private ConfigurableApplicationContext startGateway(String simulatorBaseUrl) {
      return new SpringApplicationBuilder(FepGatewayApplication.class)
          .initializers(applicationContext -> registerGatewayScenarioBeans(applicationContext, simulatorBaseUrl))
          .run(
          "--server.port=0",
          "--management.server.port=0",
          "--internal.secret=" + INTERNAL_SECRET,
          "--spring.application.name=fep-gateway",
          "--fep.simulator.base-url=" + simulatorBaseUrl,
          "--fep.simulator.chaos-probe-enabled=true",
          "--spring.datasource.url=" + inMemoryDbUrl("fep_gateway_resilience"),
          "--spring.datasource.driver-class-name=org.h2.Driver",
          "--spring.datasource.username=sa",
          "--spring.datasource.password=",
          "--spring.jpa.hibernate.ddl-auto=validate",
          "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
          "--spring.flyway.enabled=true",
          "--spring.flyway.validate-on-migrate=true",
          "--spring.flyway.locations=" + flywayLocations(FepGatewayApplication.class)
      );
    }

    private JsonNode send(HttpRequest request) throws Exception {
      HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      return OBJECT_MAPPER.readTree(response.body());
    }

    private String baseUrl(ConfigurableApplicationContext context) {
      WebServerApplicationContext webServerApplicationContext = (WebServerApplicationContext) context;
      return "http://127.0.0.1:" + webServerApplicationContext.getWebServer().getPort();
    }

    private String inMemoryDbUrl(String prefix) {
      return "jdbc:h2:mem:" + prefix + "_" + UUID.randomUUID().toString().replace("-", "")
          + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    private String flywayLocations(Class<?> applicationClass) {
      Path resourcesDir = resourcesDirectory(applicationClass);
      return "filesystem:" + resourcesDir.resolve("migration")
          + ",filesystem:" + resourcesDir.resolve("seed");
    }

    private void registerGatewayScenarioBeans(ConfigurableApplicationContext applicationContext, String simulatorBaseUrl) {
      GenericApplicationContext genericApplicationContext = (GenericApplicationContext) applicationContext;
      genericApplicationContext.registerBean(SubmitCounter.class, SubmitCounter::new);
      genericApplicationContext.registerBean(
          "countingFixDataPlaneService",
          FixDataPlaneService.class,
          () -> new CountingFixDataPlaneService(
              genericApplicationContext.getBean(RestClient.Builder.class).baseUrl(simulatorBaseUrl).build(),
              Boolean.parseBoolean(
                  genericApplicationContext.getEnvironment().getProperty("fep.simulator.chaos-probe-enabled", "false")
              ),
              genericApplicationContext.getBean(SubmitCounter.class)
          ),
          beanDefinition -> beanDefinition.setPrimary(true)
      );
    }
  }

  private static String corebankFlywayLocations() {
    Path resourcesDir = resourcesDirectory(CorebankServiceApplication.class);
    return "filesystem:" + resourcesDir.resolve("migration")
        + ",filesystem:" + resourcesDir.resolve("seed");
  }

  private static Path resourcesDirectory(Class<?> applicationClass) {
    try {
      Path codeSource = Path.of(applicationClass.getProtectionDomain().getCodeSource().getLocation().toURI()).normalize();
      Path cursor = Files.isDirectory(codeSource) ? codeSource : codeSource.getParent();
      while (cursor != null && (cursor.getFileName() == null || !"build".equals(cursor.getFileName().toString()))) {
        cursor = cursor.getParent();
      }
      if (cursor == null) {
        throw new IllegalStateException("Failed to locate Gradle build directory for " + applicationClass.getSimpleName());
      }
      return cursor.resolve("resources/main/db");
    } catch (URISyntaxException ex) {
      throw new IllegalStateException("Failed to resolve resources directory for " + applicationClass.getSimpleName(), ex);
    }
  }
}
