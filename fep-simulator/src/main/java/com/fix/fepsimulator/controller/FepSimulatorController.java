package com.fix.fepsimulator.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fix.common.web.CorrelationIdSupport;
import com.fix.common.web.TraceparentSupport;
import io.swagger.v3.oas.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.fepsimulator.service.FepSimulatorControlService;

@RestController
public class FepSimulatorController {

  private static final Logger log = LoggerFactory.getLogger(FepSimulatorController.class);

  private final FepSimulatorControlService fepSimulatorControlService;

  public FepSimulatorController(FepSimulatorControlService fepSimulatorControlService) {
    this.fepSimulatorControlService = fepSimulatorControlService;
  }

  @GetMapping("/api/v1/ping")
  public Map<String, Object> ping(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String exchange,
      @RequestParam(required = false) Long amount
  ) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("service", "fep-simulator");
    response.put("status", "ok");
    if (exchange != null && !exchange.isBlank()) {
      String action = fepSimulatorControlService.resolveMatchingAction(symbol, exchange, amount)
          .map(Enum::name)
          .orElse("NONE");
      response.put("chaosAction", action);
    }
    return response;
  }

  @GetMapping("/api/v1/errors/boom")
  public void boom() {
    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "simulator bad request");
  }

  @GetMapping("/fep-internal/v1/ping")
  public Map<String, String> internalPing(
      @Parameter(hidden = true)
      @org.springframework.web.bind.annotation.RequestHeader(com.fix.common.web.CommonHeaders.X_CORRELATION_ID)
      String correlationId,
      @Parameter(hidden = true)
      @org.springframework.web.bind.annotation.RequestHeader(com.fix.common.web.CommonHeaders.TRACEPARENT)
      String traceparent
  ) {
    String ensuredCorrelationId = CorrelationIdSupport.currentOrGenerate();
    String ensuredTraceparent = TraceparentSupport.currentOrGenerate();
    log.info(
        "operation=SIMULATOR_TRACE_DIAGNOSTIC_RECEIVED correlationId={} traceparent={} boundary=open",
        ensuredCorrelationId,
        ensuredTraceparent
    );
    return Map.of("service", "fep-simulator", "boundary", "open");
  }
}
