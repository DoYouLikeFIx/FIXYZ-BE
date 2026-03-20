package com.fix.fepgateway.dataplane.marketdata.kis;

import java.nio.charset.StandardCharsets;

public record KisDecryptionContext(String key, String iv) {

  private static final int AES_256_KEY_BYTES = 32;
  private static final int AES_CBC_IV_BYTES = 16;

  public KisDecryptionContext {
    requireNonBlank(key, "key");
    requireNonBlank(iv, "iv");
    validateUtf8Length(key, AES_256_KEY_BYTES, "key");
    validateUtf8Length(iv, AES_CBC_IV_BYTES, "iv");
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }

  private static void validateUtf8Length(String value, int expectedLength, String fieldName) {
    if (value.getBytes(StandardCharsets.UTF_8).length != expectedLength) {
      throw new IllegalArgumentException(fieldName + " must be " + expectedLength + " bytes");
    }
  }
}
