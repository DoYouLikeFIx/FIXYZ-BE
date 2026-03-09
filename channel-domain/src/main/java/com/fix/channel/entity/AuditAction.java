package com.fix.channel.entity;

public enum AuditAction {
  AUTH_REGISTER,
  AUTH_LOGIN_FAILURE,
  AUTH_LOGIN_SUCCESS,
  LOGOUT,
  MEMBER_PROFILE_UPDATE,
  MEMBER_PASSWORD_UPDATE,
  ORDER_SESSION_CREATE;

  public String value() {
    return name();
  }
}
