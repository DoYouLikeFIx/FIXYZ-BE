package com.fix.common.error;

public class RetryAfterBusinessException extends BusinessException {

  private final long retryAfterSeconds;

  public RetryAfterBusinessException(ErrorCode errorCode, String message, long retryAfterSeconds) {
    super(errorCode, message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
