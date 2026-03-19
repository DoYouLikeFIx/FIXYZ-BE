package com.fix.corebank.client;

public record FepReplayPayload(
    String clOrdId,
    String manualDecision,
    String operatorId,
    String approvedBy,
    String evidenceRef,
    String reason,
    Long executionPrice
) {
}
