package com.fix.fepsimulator.vo;

public class SimulatorChaosResult {

  private final String connectionKey;
  private final String scenario;
  private final String status;

  private SimulatorChaosResult(String connectionKey, String scenario, String status) {
    this.connectionKey = connectionKey;
    this.scenario = scenario;
    this.status = status;
  }

  public static SimulatorChaosResult of(String connectionKey, String scenario, String status) {
    return new SimulatorChaosResult(connectionKey, scenario, status);
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
