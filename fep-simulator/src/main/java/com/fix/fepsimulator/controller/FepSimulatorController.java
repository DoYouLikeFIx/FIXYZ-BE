package com.fix.fepsimulator.controller;

import com.fix.common.error.ErrorCode;
import com.fix.common.error.BusinessException;
import com.fix.fepsimulator.service.FepSimulatorControlService;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FepSimulatorController {

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
  public Map<String, String> internalPing() {
    return Map.of("service", "fep-simulator", "boundary", "open");
  }
}
