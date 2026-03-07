package com.fix.fepsimulator.vo;

public class SimulatorChaosCommand {

  private final String connectionKey;
  private final String scenario;
  private final int intensity;

  private SimulatorChaosCommand(String connectionKey, String scenario, int intensity) {
    this.connectionKey = connectionKey;
    this.scenario = scenario;
    this.intensity = intensity;
  }

  public static SimulatorChaosCommand of(String connectionKey, String scenario, int intensity) {
    return new SimulatorChaosCommand(connectionKey, scenario, intensity);
  }

  public String getConnectionKey() {
    return connectionKey;
  }

  public String getScenario() {
    return scenario;
  }

  public int getIntensity() {
    return intensity;
  }
}
