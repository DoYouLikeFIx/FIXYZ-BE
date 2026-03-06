package com.fix.fepsimulator.dto.request;

import com.fix.fepsimulator.vo.SimulatorChaosCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SimulatorChaosRequest {

  @NotBlank
  private String connectionKey;

  @NotBlank
  private String scenario;

  @NotNull
  @Min(1)
  @Max(100)
  private Integer intensity;

  public SimulatorChaosCommand toVo() {
    return SimulatorChaosCommand.of(connectionKey, scenario, intensity);
  }

  public String getConnectionKey() {
    return connectionKey;
  }

  public void setConnectionKey(String connectionKey) {
    this.connectionKey = connectionKey;
  }

  public String getScenario() {
    return scenario;
  }

  public void setScenario(String scenario) {
    this.scenario = scenario;
  }

  public Integer getIntensity() {
    return intensity;
  }

  public void setIntensity(Integer intensity) {
    this.intensity = intensity;
  }
}
