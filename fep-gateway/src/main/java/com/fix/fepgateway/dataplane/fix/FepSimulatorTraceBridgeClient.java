package com.fix.fepgateway.dataplane.fix;

import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.common.web.TraceparentSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class FepSimulatorTraceBridgeClient {

  private static final Logger log = LoggerFactory.getLogger(FepSimulatorTraceBridgeClient.class);
  private static final String INTERNAL_PING_PATH = "/fep-internal/v1/ping";

  private final RestClient restClient;
  private final String internalSecret;

  public FepSimulatorTraceBridgeClient(
      RestClient.Builder restClientBuilder,
      @Value("${fep.simulator.diagnostics-base-url:http://fep-simulator:8082}") String baseUrl,
      @Value("${internal.secret:local-internal-secret}") String internalSecret
  ) {
    this.restClient = restClientBuilder
        .requestFactory(createRequestFactory())
        .baseUrl(baseUrl)
        .build();
    this.internalSecret = internalSecret;
  }

  public TraceBridgeResult bridgeTrace(String correlationId, String traceparent) {
    String normalizedCorrelationId = CorrelationIdSupport.normalize(correlationId);
    String normalizedTraceparent = TraceparentSupport.normalize(traceparent);
    if (normalizedCorrelationId == null || normalizedTraceparent == null) {
      return new TraceBridgeResult(
          normalizedCorrelationId,
          normalizedTraceparent,
          false,
          "missing or invalid diagnostic trace headers"
      );
    }

    try {
      restClient.get()
          .uri(INTERNAL_PING_PATH)
          .header(CommonHeaders.X_INTERNAL_SECRET, internalSecret)
          .header(CommonHeaders.X_CORRELATION_ID, normalizedCorrelationId)
          .header(CommonHeaders.TRACEPARENT, normalizedTraceparent)
          .retrieve()
          .toBodilessEntity();
      log.info(
          "operation=SIMULATOR_TRACE_DIAGNOSTIC correlationId={} traceparent={} result=forwarded",
          normalizedCorrelationId,
          normalizedTraceparent
      );
      return new TraceBridgeResult(normalizedCorrelationId, normalizedTraceparent, true, null);
    } catch (RestClientException ex) {
      log.warn(
          "operation=SIMULATOR_TRACE_DIAGNOSTIC correlationId={} traceparent={} message={}",
          normalizedCorrelationId,
          normalizedTraceparent,
          ex.getMessage()
      );
      return new TraceBridgeResult(normalizedCorrelationId, normalizedTraceparent, false, ex.getMessage());
    }
  }

  private static SimpleClientHttpRequestFactory createRequestFactory() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(200);
    requestFactory.setReadTimeout(200);
    return requestFactory;
  }

  public record TraceBridgeResult(
      String correlationId,
      String traceparent,
      boolean forwarded,
      String message
  ) {
  }
}
