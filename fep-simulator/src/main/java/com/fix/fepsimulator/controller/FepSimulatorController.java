package com.fix.fepsimulator.controller;

import com.fix.common.error.ErrorCode;
import com.fix.common.error.FixException;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FepSimulatorController {

  @GetMapping("/api/v1/ping")
  public Map<String, String> ping() {
    return Map.of("service", "fep-simulator", "status", "ok");
  }

  @GetMapping("/api/v1/errors/boom")
  public void boom() {
    throw new FixException(ErrorCode.BAD_REQUEST, "simulator bad request");
  }

  @GetMapping("/fep-internal/v1/ping")
  public Map<String, String> internalPing() {
    return Map.of("service", "fep-simulator", "boundary", "open");
  }
}
