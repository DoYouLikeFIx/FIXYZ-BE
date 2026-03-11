package com.fix.channel.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.channel.vo.AdminAccountStatusTransitionResult;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminAccountStatusTransitionResponse(
    Long accountId,
    Long memberId,
    String previousStatus,
    String newStatus,
    boolean changed,
    Long eventId,
    String reason,
    String actor,
    String context,
    Instant asOf
) {

  public static AdminAccountStatusTransitionResponse from(AdminAccountStatusTransitionResult result) {
    return new AdminAccountStatusTransitionResponse(
        result.getAccountId(),
        result.getMemberId(),
        result.getPreviousStatus(),
        result.getNewStatus(),
        result.isChanged(),
        result.getEventId(),
        result.getReason(),
        result.getActor(),
        result.getContext(),
        result.getAsOf()
    );
  }
}
