package com.fix.channel.entity;

public enum OrderSessionStatus {
  PENDING_NEW,
  AUTHED,
  EXECUTING,
  REQUERYING,
  ESCALATED,
  COMPLETED,
  FAILED,
  CANCELED,
  EXPIRED;

  public boolean canTransitionTo(OrderSessionStatus nextStatus) {
    return switch (this) {
      case PENDING_NEW -> nextStatus == AUTHED || nextStatus == FAILED || nextStatus == EXPIRED;
      case AUTHED -> nextStatus == EXECUTING || nextStatus == EXPIRED;
      case EXECUTING ->
          nextStatus == COMPLETED
              || nextStatus == FAILED
              || nextStatus == CANCELED
              || nextStatus == REQUERYING
              || nextStatus == ESCALATED;
      case REQUERYING -> nextStatus == COMPLETED || nextStatus == CANCELED || nextStatus == ESCALATED;
      case ESCALATED -> nextStatus == COMPLETED || nextStatus == FAILED || nextStatus == CANCELED;
      case COMPLETED, FAILED, CANCELED, EXPIRED -> false;
    };
  }

  public boolean isActiveWindow() {
    return this == PENDING_NEW || this == AUTHED;
  }
}
