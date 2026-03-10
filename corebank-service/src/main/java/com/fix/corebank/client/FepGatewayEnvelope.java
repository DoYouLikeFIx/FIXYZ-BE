package com.fix.corebank.client;

public record FepGatewayEnvelope<T>(
    boolean success,
    T data,
    Object error
) {
}
