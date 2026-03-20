package com.fix.fepgateway.dataplane.marketdata.kis;

import java.util.Objects;

public class KisFrameParseException extends RuntimeException {

  private final KisFrameFailureType failureType;

  public KisFrameParseException(KisFrameFailureType failureType, String message) {
    super(message);
    this.failureType = Objects.requireNonNull(failureType, "failureType must not be null");
  }

  public KisFrameParseException(KisFrameFailureType failureType, String message, Throwable cause) {
    super(message, cause);
    this.failureType = Objects.requireNonNull(failureType, "failureType must not be null");
  }

  public KisFrameFailureType getFailureType() {
    return failureType;
  }
}
