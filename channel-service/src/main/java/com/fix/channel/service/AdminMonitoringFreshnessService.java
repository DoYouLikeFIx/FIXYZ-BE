package com.fix.channel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fix.channel.vo.AdminMonitoringFreshnessResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AdminMonitoringFreshnessService {

  private static final List<PanelDefinition> PANEL_DEFINITIONS = List.of(
      new PanelDefinition(
          "executionVolume",
          "channel-service",
          "channel_order_execution_last_completed_epoch_seconds"
      ),
      new PanelDefinition(
          "pendingSessions",
          "channel-service",
          "channel_order_sessions_recovery_backlog_last_updated_epoch_seconds"
      ),
      new PanelDefinition(
          "marketDataIngest",
          "fep-gateway",
          "fep_marketdata_snapshots_last_persisted_epoch_seconds"
      )
  );

  private final RestClient restClient;
  private final Clock clock;
  private final long liveMaxAgeSeconds;
  private final long unavailableMaxAgeSeconds;
  private final long cacheTtlMillis;
  private volatile CachedFreshness cachedFreshness;

  @Autowired
  public AdminMonitoringFreshnessService(
      RestClient.Builder restClientBuilder,
      Clock clock,
      @Value("${observability.prometheus.base-url:${OBSERVABILITY_PROMETHEUS_BASE_URL:http://127.0.0.1:9090}}")
      String prometheusBaseUrl,
      @Value("${observability.freshness.live-max-age:60s}") Duration liveMaxAge,
      @Value("${observability.freshness.unavailable-max-age:300s}") Duration unavailableMaxAge,
      @Value("${observability.freshness.cache-ttl:5s}") Duration cacheTtl
  ) {
    this(restClientBuilder.baseUrl(prometheusBaseUrl).build(), clock, liveMaxAge, unavailableMaxAge, cacheTtl);
  }

  AdminMonitoringFreshnessService(
      RestClient restClient,
      Clock clock,
      Duration liveMaxAge,
      Duration unavailableMaxAge,
      Duration cacheTtl
  ) {
    this.restClient = restClient;
    this.clock = clock;
    this.liveMaxAgeSeconds = Math.max(1L, liveMaxAge.getSeconds());
    this.unavailableMaxAgeSeconds = Math.max(this.liveMaxAgeSeconds, unavailableMaxAge.getSeconds());
    this.cacheTtlMillis = Math.max(0L, cacheTtl.toMillis());
  }

  public AdminMonitoringFreshnessResult getFreshness() {
    Instant checkedAt = Instant.now(clock);
    CachedFreshness cached = cachedFreshness;

    if (cached != null && cached.isFreshAt(checkedAt, cacheTtlMillis)) {
      return cached.response();
    }

    synchronized (this) {
      cached = cachedFreshness;
      if (cached != null && cached.isFreshAt(checkedAt, cacheTtlMillis)) {
        return cached.response();
      }

      AdminMonitoringFreshnessResult response = new AdminMonitoringFreshnessResult(
          PANEL_DEFINITIONS.stream().map(definition -> resolveFreshness(definition, checkedAt)).toList()
      );
      cachedFreshness = new CachedFreshness(checkedAt, response);
      return response;
    }
  }

  AdminMonitoringFreshnessResult.Item resolveFreshness(PanelDefinition definition, Instant checkedAt) {
    try {
      Double targetUp = fetchPrometheusInstantValue(String.format("max(up{job=\"%s\"})", definition.targetJob()));
      Double freshnessEpochSeconds = fetchPrometheusInstantValue(String.format("max(%s)", definition.freshnessMetric()));
      Instant lastUpdatedAt = toLastUpdatedAt(freshnessEpochSeconds);

      if (targetUp == null || targetUp < 1.0d) {
        return unavailable(
            definition.key(),
            lastUpdatedAt,
            "Prometheus target unavailable (" + definition.targetJob() + ")"
        );
      }

      if (lastUpdatedAt == null) {
        return unavailable(
            definition.key(),
            null,
            "Freshness metric unavailable (" + definition.freshnessMetric() + ")"
        );
      }

      long ageSeconds = Math.max(0L, Duration.between(lastUpdatedAt, checkedAt).getSeconds());
      String status = resolveStatus(ageSeconds);

      return new AdminMonitoringFreshnessResult.Item(
          definition.key(),
          status,
          buildStatusMessage(status, ageSeconds),
          lastUpdatedAt
      );
    } catch (RestClientException | IllegalStateException ex) {
      return unavailable(definition.key(), null, "Prometheus freshness lookup failed");
    }
  }

  protected Double fetchPrometheusInstantValue(String expression) {
    JsonNode payload = restClient.get()
        .uri((uriBuilder) -> uriBuilder.path("/api/v1/query").queryParam("query", expression).build())
        .retrieve()
        .body(JsonNode.class);

    if (payload == null || !"success".equals(payload.path("status").asText())) {
      throw new IllegalStateException("Prometheus query did not return success");
    }

    JsonNode rows = payload.path("data").path("result");
    if (!rows.isArray()) {
      return null;
    }

    Double maxValue = null;
    for (JsonNode row : rows) {
      JsonNode valueNode = row.path("value");
      if (!valueNode.isArray() || valueNode.size() < 2) {
        continue;
      }

      String rawValue = valueNode.get(1).asText();

      try {
        double parsed = Double.parseDouble(rawValue);
        if (!Double.isFinite(parsed)) {
          continue;
        }
        maxValue = maxValue == null ? parsed : Math.max(maxValue, parsed);
      } catch (NumberFormatException ignored) {
        // Ignore malformed Prometheus samples and continue scanning the vector.
      }
    }

    return maxValue;
  }

  private AdminMonitoringFreshnessResult.Item unavailable(String key, Instant lastUpdatedAt, String statusMessage) {
    return new AdminMonitoringFreshnessResult.Item(
        key,
        "unavailable",
        statusMessage,
        lastUpdatedAt
    );
  }

  private Instant toLastUpdatedAt(Double freshnessEpochSeconds) {
    if (freshnessEpochSeconds == null || freshnessEpochSeconds <= 0.0d) {
      return null;
    }
    return Instant.ofEpochSecond((long) Math.floor(freshnessEpochSeconds));
  }

  private String resolveStatus(long ageSeconds) {
    if (ageSeconds <= liveMaxAgeSeconds) {
      return "live";
    }

    if (ageSeconds <= unavailableMaxAgeSeconds) {
      return "stale";
    }

    return "unavailable";
  }

  private String buildStatusMessage(String status, long ageSeconds) {
    String ageLabel = formatAge(ageSeconds);

    return switch (status) {
      case "live" -> "Prometheus freshness healthy (" + ageLabel + " old)";
      case "stale" -> "Prometheus freshness stale (" + ageLabel + " old)";
      default -> "Prometheus freshness unavailable (" + ageLabel + " old)";
    };
  }

  private String formatAge(long ageSeconds) {
    if (ageSeconds < 60L) {
      return ageSeconds + "s";
    }

    if (ageSeconds < 3600L) {
      long minutes = ageSeconds / 60L;
      long seconds = ageSeconds % 60L;
      return seconds == 0L ? minutes + "m" : minutes + "m " + seconds + "s";
    }

    long hours = ageSeconds / 3600L;
    long minutes = (ageSeconds % 3600L) / 60L;
    return minutes == 0L ? hours + "h" : hours + "h " + minutes + "m";
  }

  private record PanelDefinition(
      String key,
      String targetJob,
      String freshnessMetric
  ) {
  }

  private record CachedFreshness(
      Instant generatedAt,
      AdminMonitoringFreshnessResult response
  ) {
    private boolean isFreshAt(Instant checkedAt, long cacheTtlMillis) {
      if (cacheTtlMillis <= 0L) {
        return false;
      }
      return Duration.between(generatedAt, checkedAt).toMillis() < cacheTtlMillis;
    }
  }
}
