package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.channel.vo.AdminMonitoringFreshnessResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AdminMonitoringFreshnessServiceTest {

  @Test
  void shouldResolveLiveStaleAndUnavailableStatesFromPrometheusSignals() {
    AdminMonitoringFreshnessService service = new StubAdminMonitoringFreshnessService(
        Map.of(
            "max(up{job=\"channel-service\"})", 1.0d,
            "max(channel_order_execution_last_completed_epoch_seconds)", epochSeconds("2026-03-24T09:19:30Z"),
            "max(channel_order_sessions_recovery_backlog_last_updated_epoch_seconds)",
            epochSeconds("2026-03-24T09:15:00Z"),
            "max(up{job=\"fep-gateway\"})", 0.0d,
            "max(fep_marketdata_snapshots_last_persisted_epoch_seconds)", epochSeconds("2026-03-24T09:11:00Z")
        ),
        Clock.fixed(Instant.parse("2026-03-24T09:20:00Z"), ZoneOffset.UTC)
    );

    AdminMonitoringFreshnessResult response = service.getFreshness();

    assertThat(response.items()).extracting(AdminMonitoringFreshnessResult.Item::key)
        .containsExactly("executionVolume", "pendingSessions", "marketDataIngest");

    assertThat(response.items().get(0).status()).isEqualTo("live");
    assertThat(response.items().get(0).lastUpdatedAt()).isEqualTo(Instant.parse("2026-03-24T09:19:30Z"));

    assertThat(response.items().get(1).status()).isEqualTo("stale");
    assertThat(response.items().get(1).lastUpdatedAt()).isEqualTo(Instant.parse("2026-03-24T09:15:00Z"));

    assertThat(response.items().get(2).status()).isEqualTo("unavailable");
    assertThat(response.items().get(2).statusMessage()).contains("target unavailable");
    assertThat(response.items().get(2).lastUpdatedAt()).isEqualTo(Instant.parse("2026-03-24T09:11:00Z"));
  }

  @Test
  void shouldCachePrometheusLookupsWithinTheConfiguredTtl() {
    CountingAdminMonitoringFreshnessService service = new CountingAdminMonitoringFreshnessService(
        Map.of(
            "max(up{job=\"channel-service\"})", 1.0d,
            "max(channel_order_execution_last_completed_epoch_seconds)", epochSeconds("2026-03-24T09:19:30Z"),
            "max(channel_order_sessions_recovery_backlog_last_updated_epoch_seconds)",
            epochSeconds("2026-03-24T09:15:00Z"),
            "max(up{job=\"fep-gateway\"})", 1.0d,
            "max(fep_marketdata_snapshots_last_persisted_epoch_seconds)", epochSeconds("2026-03-24T09:18:00Z")
        ),
        Clock.fixed(Instant.parse("2026-03-24T09:20:00Z"), ZoneOffset.UTC)
    );

    AdminMonitoringFreshnessResult first = service.getFreshness();
    AdminMonitoringFreshnessResult second = service.getFreshness();

    assertThat(second).isEqualTo(first);
    assertThat(service.lookupCount()).isEqualTo(6);
  }

  private static double epochSeconds(String timestamp) {
    return Instant.parse(timestamp).getEpochSecond();
  }

  private static final class StubAdminMonitoringFreshnessService extends AdminMonitoringFreshnessService {

    private final Map<String, Double> valuesByExpression;

    private StubAdminMonitoringFreshnessService(Map<String, Double> valuesByExpression, Clock clock) {
      super(
          RestClient.builder().baseUrl("http://127.0.0.1:9090").build(),
          clock,
          Duration.ofSeconds(60),
          Duration.ofSeconds(300),
          Duration.ofSeconds(5)
      );
      this.valuesByExpression = valuesByExpression;
    }

    @Override
    protected Double fetchPrometheusInstantValue(String expression) {
      return valuesByExpression.get(expression);
    }
  }

  private static final class CountingAdminMonitoringFreshnessService extends AdminMonitoringFreshnessService {

    private final Map<String, Double> valuesByExpression;
    private final AtomicInteger lookupCount = new AtomicInteger();

    private CountingAdminMonitoringFreshnessService(Map<String, Double> valuesByExpression, Clock clock) {
      super(
          RestClient.builder().baseUrl("http://127.0.0.1:9090").build(),
          clock,
          Duration.ofSeconds(60),
          Duration.ofSeconds(300),
          Duration.ofSeconds(5)
      );
      this.valuesByExpression = valuesByExpression;
    }

    @Override
    protected Double fetchPrometheusInstantValue(String expression) {
      lookupCount.incrementAndGet();
      return valuesByExpression.get(expression);
    }

    private int lookupCount() {
      return lookupCount.get();
    }
  }
}
