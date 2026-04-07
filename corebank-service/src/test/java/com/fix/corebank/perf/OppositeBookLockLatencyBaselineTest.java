package com.fix.corebank.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.service.CorebankOppositeBookQueryService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "internal.secret=test-secret",
    "corebank.ledger-integrity.enabled=false"
})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_opposite_book_perf;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class OppositeBookLockLatencyBaselineTest {

  private static final String SCENARIO_ID = "OB-PERF-BASELINE-001";
  private static final String SYMBOL_PREFIX = "OB-PERF-";
  private static final int REPRESENTATIVE_REQUIRED_ROWS = 3;
  private static final List<Integer> DEFAULT_BOOK_DEPTHS = List.of(100, 1000, 5000);
  private static final int WARMUP_ITERATIONS = integerProperty("oppositeBook.perf.warmupIterations", 2);
  private static final int MEASURED_ITERATIONS = integerProperty("oppositeBook.perf.measuredIterations", 5);

  @Autowired
  private CorebankOppositeBookQueryService oppositeBookQueryService;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM orders");
  }

  @Test
  @Tag("opposite-book-performance")
  void shouldCaptureBaselineLatencyAcrossOppositeBookDepths() throws Exception {
    List<DepthLatencyResult> depthResults = new ArrayList<>();
    Throwable failure = null;

    try {
      validatePerfConfiguration();

      for (int bookDepth : parseBookDepths()) {
        seedRestingSellBook(bookDepth);

        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
          measureLockLatency(bookDepth);
        }

        List<Double> samplesMs = new ArrayList<>();
        int retrievedRows = 0;
        for (int iteration = 0; iteration < MEASURED_ITERATIONS; iteration++) {
          LockLatencySample sample = measureLockLatency(bookDepth);
          samplesMs.add(sample.elapsedMs());
          retrievedRows = sample.retrievedRows();
        }

        List<Double> sortedSamples = samplesMs.stream().sorted().toList();
        depthResults.add(new DepthLatencyResult(
            bookDepth,
            REPRESENTATIVE_REQUIRED_ROWS,
            retrievedRows,
            round(((double) retrievedRows) / REPRESENTATIVE_REQUIRED_ROWS),
            round(percentile(sortedSamples, 0.50d)),
            round(percentile(sortedSamples, 0.95d)),
            round(sortedSamples.get(sortedSamples.size() - 1)),
            samplesMs.stream().map(OppositeBookLockLatencyBaselineTest::round).toList()
        ));

        jdbcTemplate.update("DELETE FROM orders");
      }

      assertThat(depthResults).hasSize(parseBookDepths().size());
      assertThat(depthResults)
          .allSatisfy(result -> assertThat(result.retrievedRows()).isGreaterThanOrEqualTo(result.representativeRequiredRows()));
    } catch (Throwable throwable) {
      failure = throwable;
      throw throwable;
    } finally {
      writePerfReport(depthResults, failure);
    }
  }

  private void seedRestingSellBook(int bookDepth) {
    String symbol = scenarioSymbol(bookDepth);
    List<Order> restingOrders = new ArrayList<>(bookDepth);
    for (int index = 0; index < bookDepth; index++) {
      long accountId = 10_000L + index;
      restingOrders.add(Order.accepted(
          accountId,
          symbol + "-maker-" + index,
          symbol,
          "SELL",
          BigDecimal.ONE.setScale(4),
          BigDecimal.valueOf(70_000L + index).setScale(4)
      ));
    }
    orderRepository.saveAll(restingOrders);
    orderRepository.flush();
  }

  private LockLatencySample measureLockLatency(int bookDepth) {
    String symbol = scenarioSymbol(bookDepth);
    long startedAtNanos = System.nanoTime();
    List<Order> lockedOrders = oppositeBookQueryService.lockExecutionCandidates(symbol, "BUY");
    long elapsedNanos = System.nanoTime() - startedAtNanos;
    return new LockLatencySample(elapsedNanos / 1_000_000.0d, lockedOrders.size());
  }

  private void writePerfReport(List<DepthLatencyResult> depthResults, Throwable failure) throws Exception {
    String outputPath = System.getProperty("oppositeBook.perf.outputPath");
    if (outputPath == null || outputPath.isBlank()) {
      return;
    }

    Path reportPath = Path.of(outputPath);
    Path parentPath = reportPath.getParent();
    if (parentPath != null) {
      Files.createDirectories(parentPath);
    }

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("scenarioId", SCENARIO_ID);
    report.put("generatedAt", Instant.now().toString());
    report.put("warmupIterations", WARMUP_ITERATIONS);
    report.put("measuredIterations", MEASURED_ITERATIONS);
    report.put("depths", parseBookDepths());
    report.put("results", depthResults);
    report.put("result", failure == null ? "PASSED" : "FAILED");
    report.put("failureType", failure == null ? null : failure.getClass().getName());
    report.put("failureMessage", failure == null ? null : failure.getMessage());

    objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
  }

  private void validatePerfConfiguration() {
    if (WARMUP_ITERATIONS < 0) {
      throw new IllegalStateException("oppositeBook.perf.warmupIterations must be >= 0");
    }
    if (MEASURED_ITERATIONS <= 0) {
      throw new IllegalStateException("oppositeBook.perf.measuredIterations must be > 0");
    }
    if (parseBookDepths().isEmpty()) {
      throw new IllegalStateException("oppositeBook.perf.bookDepths must not be empty");
    }
  }

  private static List<Integer> parseBookDepths() {
    String configured = System.getProperty("oppositeBook.perf.bookDepths");
    if (configured == null || configured.isBlank()) {
      return DEFAULT_BOOK_DEPTHS;
    }
    return configured.lines()
        .flatMap(line -> java.util.Arrays.stream(line.split(",")))
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .map(Integer::parseInt)
        .toList();
  }

  private static int integerProperty(String key, int defaultValue) {
    String configured = System.getProperty(key);
    if (configured == null || configured.isBlank()) {
      return defaultValue;
    }
    return Integer.parseInt(configured.trim());
  }

  private static double percentile(List<Double> sortedSamples, double percentile) {
    int index = (int) Math.ceil(percentile * sortedSamples.size()) - 1;
    int boundedIndex = Math.max(0, Math.min(index, sortedSamples.size() - 1));
    return sortedSamples.get(boundedIndex);
  }

  private static double round(double value) {
    return Math.round(value * 1_000.0d) / 1_000.0d;
  }

  private static String scenarioSymbol(int bookDepth) {
    return SYMBOL_PREFIX + bookDepth;
  }

  private record LockLatencySample(
      double elapsedMs,
      int retrievedRows
  ) {
  }

  private record DepthLatencyResult(
      int bookDepth,
      int representativeRequiredRows,
      int retrievedRows,
      double retrievalAmplification,
      double p50Ms,
      double p95Ms,
      double maxMs,
      List<Double> samplesMs
  ) {
  }
}
