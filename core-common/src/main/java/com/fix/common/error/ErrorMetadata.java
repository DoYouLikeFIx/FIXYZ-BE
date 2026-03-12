package com.fix.common.error;

import java.util.Map;

public record ErrorMetadata(
    String userMessageKey,
    String operatorCode,
    Map<String, Object> additionalProperties
) {

  public ErrorMetadata(String userMessageKey, String operatorCode) {
    this(userMessageKey, operatorCode, Map.of());
  }
}
