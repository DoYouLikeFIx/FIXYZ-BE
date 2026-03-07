package com.fix.fepsimulator.dto.response;

import com.fix.fepsimulator.vo.SimulatorChaosResult;

public class SimulatorChaosResponse {

  private final String connectionKey;
  private final String scenario;
  private final String status;

  private SimulatorChaosResponse(String connectionKey, String scenario, String status) {
    this.connectionKey = connectionKey;
    this.scenario = scenario;
    this.status = status;
  }

  public static SimulatorChaosResponse from(SimulatorChaosResult result) {
    return new SimulatorChaosResponse(result.getConnectionKey(), result.getScenario(), result.getStatus());
  }

  public String getConnectionKey() {
    return connectionKey;
  }

  public String getScenario() {
    return scenario;
  }

  public String getStatus() {
    return status;
  }
}
