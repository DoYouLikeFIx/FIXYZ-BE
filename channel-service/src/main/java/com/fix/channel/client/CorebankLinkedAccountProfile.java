package com.fix.channel.client;

public record CorebankLinkedAccountProfile(
    Long accountId,
    Long memberId,
    String accountNumber
) {
}
