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
  EXPIRED
}
