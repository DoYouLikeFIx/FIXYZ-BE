package com.fix.corebank.vo;

public class InternalOrderRequeryCommand {

  private final String clOrdId;
  private final int attemptCount;

  private InternalOrderRequeryCommand(String clOrdId, int attemptCount) {
    this.clOrdId = clOrdId;
    this.attemptCount = attemptCount;
  }

  public static InternalOrderRequeryCommand of(String clOrdId) {
    return new InternalOrderRequeryCommand(clOrdId, 1);
  }

  public static InternalOrderRequeryCommand of(String clOrdId, int attemptCount) {
    return new InternalOrderRequeryCommand(clOrdId, attemptCount);
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public int getAttemptCount() {
    return attemptCount;
  }
}
