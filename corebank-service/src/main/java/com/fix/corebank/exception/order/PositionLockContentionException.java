package com.fix.corebank.exception.order;

public class PositionLockContentionException extends RuntimeException {

  public PositionLockContentionException(Long accountId, String symbol, Throwable cause) {
    super("position lock contention for accountId=" + accountId + ", symbol=" + symbol, cause);
  }
}
