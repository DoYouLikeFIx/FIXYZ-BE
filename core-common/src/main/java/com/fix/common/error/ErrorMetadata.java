package com.fix.common.error;

public record ErrorMetadata(
    String userMessageKey,
    String operatorCode
) {
}
