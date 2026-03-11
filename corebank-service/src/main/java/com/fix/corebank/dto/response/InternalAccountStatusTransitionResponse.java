package com.fix.corebank.dto.response;

import com.fix.corebank.vo.AccountStatusTransitionResult;
import java.time.Instant;

public class InternalAccountStatusTransitionResponse {

  private final Long accountId;
  private final Long memberId;
  private final String previousStatus;
  private final String newStatus;
  private final boolean changed;
  private final Long eventId;
  private final String reason;
  private final String actor;
  private final String context;
  private final Instant asOf;

  private InternalAccountStatusTransitionResponse(
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
    this.accountId = accountId;
    this.memberId = memberId;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.changed = changed;
    this.eventId = eventId;
    this.reason = reason;
    this.actor = actor;
    this.context = context;
    this.asOf = asOf;
  }

  public static InternalAccountStatusTransitionResponse from(AccountStatusTransitionResult result) {
    return new InternalAccountStatusTransitionResponse(
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

  public Long getAccountId() {
    return accountId;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getPreviousStatus() {
    return previousStatus;
  }

  public String getNewStatus() {
    return newStatus;
  }

  public boolean isChanged() {
    return changed;
  }

  public Long getEventId() {
    return eventId;
  }

  public String getReason() {
    return reason;
  }

  public String getActor() {
    return actor;
  }

  public String getContext() {
    return context;
  }

  public Instant getAsOf() {
    return asOf;
  }
}
