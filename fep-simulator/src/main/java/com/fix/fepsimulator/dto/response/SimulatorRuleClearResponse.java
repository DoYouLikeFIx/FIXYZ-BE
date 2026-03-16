package com.fix.fepsimulator.dto.response;

public class SimulatorRuleClearResponse {

  private final String message;
  private final int clearedCount;

  public SimulatorRuleClearResponse(String message, int clearedCount) {
    this.message = message;
    this.clearedCount = clearedCount;
  }

  public String getMessage() {
    return message;
  }

  public int getClearedCount() {
    return clearedCount;
  }
}