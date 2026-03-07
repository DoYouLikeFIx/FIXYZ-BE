package com.fix.corebank.vo;

public class InternalOrderRequeryCommand {

  private final String clOrdId;

  private InternalOrderRequeryCommand(String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public static InternalOrderRequeryCommand of(String clOrdId) {
    return new InternalOrderRequeryCommand(clOrdId);
  }

  public String getClOrdId() {
    return clOrdId;
  }
}
