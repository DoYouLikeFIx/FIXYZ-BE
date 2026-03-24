package com.fix.channel.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.client.CorebankClient;
import com.fix.channel.repository.NotificationRepository;
import com.fix.channel.service.OrderSessionTtlStore;
import com.fix.channel.testsupport.OrderSessionTestFixture;
import com.fix.channel.vo.OrderExecuteCommand;
import com.fix.channel.vo.OrderExecuteResult;
import com.fix.common.web.CommonHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

@SpringBootTest
@AutoConfigureMockMvc
@Import({OrderSessionTestFixture.class, OrderExecuteLatencySmokeTest.TestConfig.class})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:channel_perf_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.session.store-type=none",
    "internal.secret=test-secret",
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@WithMockUser(username = "perf-user")
class OrderExecuteLatencySmokeTest {

  private static final String SCENARIO_ID = "E10-PERF-001";
  private static final long MEMBER_ID = 301L;
  private static final long ACCOUNT_ID = 1L;
  private static final String EXECUTION_LATENCY_METER = "channel.order.execution.latency";
  private static final String EXECUTION_LATENCY_OUTCOME = "completed";
  private static final String EXECUTION_LATENCY_PROMETHEUS_FAMILY = "channel_order_execution_latency_seconds";
  private static final int WARMUP_ITERATIONS = integerProperty(
      "story102.execute.perf.warmupIterations",
      "story119.execute.perf.warmupIterations",
      5
  );
  private static final int MEASURED_ITERATIONS = integerProperty(
      "story102.execute.perf.measuredIterations",
      "story119.execute.perf.measuredIterations",
      25
  );
  private static final long P95_BUDGET_MS = longProperty(
      "story102.execute.perf.p95BudgetMs",
      "story119.execute.perf.p95BudgetMs",
      1_000L
  );

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OrderSessionTestFixture orderSessionTestFixture;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private FakeCorebankClient fakeCorebankClient;

  @Autowired
  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    orderSessionTestFixture.reset();
    notificationRepository.deleteAll();
    fakeCorebankClient.reset();
  }

  @Test
  @Tag("epic10-performance")
  void e10Perf001ShouldKeepExecuteP95WithinConfiguredBudget() throws Exception {
    List<Double> latencySamplesMs = new ArrayList<>();
    Double p50Ms = null;
    Double p95Ms = null;
    Double maxMs = null;
    Timer executionLatencyTimer = null;
    Throwable failure = null;

    try {
      validatePerfConfiguration();

      for (int index = 0; index < WARMUP_ITERATIONS; index++) {
        executeAndMeasure(index);
      }

      for (int index = 0; index < MEASURED_ITERATIONS; index++) {
        latencySamplesMs.add(executeAndMeasure(index + WARMUP_ITERATIONS));
      }

      List<Double> sortedSamples = latencySamplesMs.stream().sorted().toList();
      p50Ms = percentile(sortedSamples, 0.50d);
      p95Ms = percentile(sortedSamples, 0.95d);
      maxMs = sortedSamples.get(sortedSamples.size() - 1);
      executionLatencyTimer = meterRegistry.find(EXECUTION_LATENCY_METER)
          .tag("outcome", EXECUTION_LATENCY_OUTCOME)
          .timer();

      assertThat(p95Ms)
          .as("execute endpoint p95 latency must stay within %s ms budget", P95_BUDGET_MS)
          .isLessThanOrEqualTo((double) P95_BUDGET_MS);
      assertThat(executionLatencyTimer)
          .as("expected %s timer with outcome=%s to be registered", EXECUTION_LATENCY_METER, EXECUTION_LATENCY_OUTCOME)
          .isNotNull();
      assertThat(executionLatencyTimer.count()).isEqualTo(WARMUP_ITERATIONS + MEASURED_ITERATIONS);
    } catch (Throwable throwable) {
      failure = throwable;
      throw throwable;
    } finally {
      try {
        writePerfReport(latencySamplesMs, p50Ms, p95Ms, maxMs, executionLatencyTimer, failure);
      } catch (Exception reportFailure) {
        if (failure != null) {
          failure.addSuppressed(reportFailure);
        } else {
          throw reportFailure;
        }
      }
    }
  }

  private double executeAndMeasure(int sampleIndex) throws Exception {
    String clOrdId = UUID.randomUUID().toString();
    String orderSessionId = orderSessionTestFixture.createInitiatedSessionId(
        MEMBER_ID,
        ACCOUNT_ID,
        clOrdId,
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(70_100),
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.now().plusSeconds(3_600)
    );

    long startedAt = System.nanoTime();
    MvcResult result = mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/execute", orderSessionId)
            .with(csrf())
            .sessionAttr("AUTH_MEMBER_ID", MEMBER_ID)
            .header(CommonHeaders.X_CORRELATION_ID, "perf-execute-" + sampleIndex))
        .andExpect(status().isOk())
        .andReturn();
    long elapsedNanos = System.nanoTime() - startedAt;

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("success").asBoolean()).isTrue();
    assertThat(body.path("data").path("orderSessionId").asText()).isEqualTo(orderSessionId);
    assertThat(body.path("data").path("status").asText()).isEqualTo("COMPLETED");
    assertThat(body.path("data").path("executionResult").asText()).isEqualTo("FILLED");
    assertThat(body.path("data").path("executedQty").decimalValue()).isEqualByComparingTo("1");
    assertThat(body.path("data").path("leavesQty").decimalValue()).isEqualByComparingTo("0");
    assertThat(body.path("data").path("externalSyncStatus").asText()).isEqualTo("CONFIRMED");

    return elapsedNanos / 1_000_000.0d;
  }

  private double percentile(List<Double> sortedSamples, double percentile) {
    int index = (int) Math.ceil(percentile * sortedSamples.size()) - 1;
    int boundedIndex = Math.max(0, Math.min(index, sortedSamples.size() - 1));
    return sortedSamples.get(boundedIndex);
  }

  private void validatePerfConfiguration() {
    if (WARMUP_ITERATIONS < 0) {
      throw new IllegalStateException("story119.execute.perf.warmupIterations must be >= 0");
    }
    if (MEASURED_ITERATIONS <= 0) {
      throw new IllegalStateException("story119.execute.perf.measuredIterations must be > 0");
    }
  }

  private void writePerfReport(
      List<Double> latencySamplesMs,
      Double p50Ms,
      Double p95Ms,
      Double maxMs,
      Timer executionLatencyTimer,
      Throwable failure
  ) throws Exception {
    String outputPath = firstSystemProperty("story102.execute.perf.outputPath", "story119.execute.perf.outputPath");
    if (outputPath == null || outputPath.isBlank()) {
      return;
    }

    Path reportPath = Path.of(outputPath);
    Files.createDirectories(reportPath.getParent());

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("storyId", "10.2");
    report.put("scenarioId", SCENARIO_ID);
    report.put("generatedAt", Instant.now().toString());
    report.put("warmupIterations", WARMUP_ITERATIONS);
    report.put("measuredIterations", MEASURED_ITERATIONS);
    report.put("p95BudgetMs", P95_BUDGET_MS);
    report.put("completedMeasuredIterations", latencySamplesMs.size());
    report.put("p50Ms", p50Ms == null ? null : round(p50Ms));
    report.put("p95Ms", p95Ms == null ? null : round(p95Ms));
    report.put("maxMs", maxMs == null ? null : round(maxMs));
    report.put("samplesMs", latencySamplesMs.stream().map(OrderExecuteLatencySmokeTest::round).toList());
    report.put("metricName", EXECUTION_LATENCY_METER);
    report.put("metricOutcome", EXECUTION_LATENCY_OUTCOME);
    report.put("prometheusMetricFamily", EXECUTION_LATENCY_PROMETHEUS_FAMILY);
    report.put("metricCount", executionLatencyTimer == null ? null : executionLatencyTimer.count());
    report.put(
        "metricTotalTimeMs",
        executionLatencyTimer == null ? null : round(executionLatencyTimer.totalTime(TimeUnit.MILLISECONDS))
    );
    report.put("metricMaxMs", executionLatencyTimer == null ? null : round(executionLatencyTimer.max(TimeUnit.MILLISECONDS)));
    report.put("result", failure == null && p95Ms != null && p95Ms <= P95_BUDGET_MS ? "PASSED" : "FAILED");
    report.put("failureType", failure == null ? null : failure.getClass().getName());
    report.put("failureMessage", failure == null ? null : failure.getMessage());

    objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
  }

  private static double round(double value) {
    return Math.round(value * 1_000.0d) / 1_000.0d;
  }

  private static int integerProperty(String primaryKey, String legacyKey, int defaultValue) {
    return Integer.getInteger(primaryKey, Integer.getInteger(legacyKey, defaultValue));
  }

  private static long longProperty(String primaryKey, String legacyKey, long defaultValue) {
    return Long.getLong(primaryKey, Long.getLong(legacyKey, defaultValue));
  }

  private static String firstSystemProperty(String primaryKey, String legacyKey) {
    String primaryValue = System.getProperty(primaryKey);
    if (primaryValue != null && !primaryValue.isBlank()) {
      return primaryValue;
    }
    return System.getProperty(legacyKey);
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    @Primary
    FakeCorebankClient perfCorebankClient() {
      return new FakeCorebankClient();
    }

    @Bean
    @Primary
    OrderSessionTtlStore perfOrderSessionTtlStore() {
      return new InMemoryOrderSessionTtlStore();
    }
  }

  static class InMemoryOrderSessionTtlStore implements OrderSessionTtlStore {

    private final ConcurrentMap<String, Instant> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void activate(String orderSessionId, Instant expiresAt) {
      activeSessions.put(orderSessionId, expiresAt);
    }

    @Override
    public void refresh(String orderSessionId, Instant expiresAt) {
      activeSessions.put(orderSessionId, expiresAt);
    }

    @Override
    public boolean isActive(String orderSessionId) {
      Instant expiresAt = activeSessions.get(orderSessionId);
      return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    @Override
    public void clear(String orderSessionId) {
      activeSessions.remove(orderSessionId);
    }

    @Override
    public Duration ttl() {
      return Duration.ofHours(1);
    }
  }

  static class FakeCorebankClient extends CorebankClient {

    private final AtomicLong orderIdSequence = new AtomicLong(90_000L);

    FakeCorebankClient() {
      super(RestClient.create(), "test-secret");
    }

    void reset() {
      orderIdSequence.set(90_000L);
    }

    @Override
    public OrderExecuteResult executeOrder(OrderExecuteCommand command, String correlationId) {
      return OrderExecuteResult.of(
          orderIdSequence.incrementAndGet(),
          command.getClOrdId(),
          "FILLED",
          false,
          command.getQuantity(),
          "FILLED",
          command.getQuantity(),
          BigDecimal.ZERO,
          command.getPrice() == null ? BigDecimal.valueOf(70_100) : command.getPrice(),
          "FEP-" + orderIdSequence.get(),
          "CONFIRMED",
          Instant.parse("2026-03-24T00:00:00Z")
      );
    }
  }
}
