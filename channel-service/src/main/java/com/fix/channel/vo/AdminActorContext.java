package com.fix.channel.vo;

public class AdminActorContext {

  private final Long adminMemberId;
  private final String operatorId;
  private final String adminEmail;
  private final String sessionId;
  private final String clientIp;
  private final String userAgent;
  private final String correlationId;

  private AdminActorContext(
      Long adminMemberId,
      String operatorId,
      String adminEmail,
      String sessionId,
      String clientIp,
      String userAgent,
      String correlationId
  ) {
    this.adminMemberId = adminMemberId;
    this.operatorId = operatorId;
    this.adminEmail = adminEmail;
    this.sessionId = sessionId;
    this.clientIp = clientIp;
    this.userAgent = userAgent;
    this.correlationId = correlationId;
  }

  public static AdminActorContext of(
      Long adminMemberId,
      String operatorId,
      String adminEmail,
      String sessionId,
      String clientIp,
      String userAgent,
      String correlationId
  ) {
    return new AdminActorContext(adminMemberId, operatorId, adminEmail, sessionId, clientIp, userAgent, correlationId);
  }

  public Long getAdminMemberId() {
    return adminMemberId;
  }

  public String getOperatorId() {
    return operatorId;
  }

  public String getAdminEmail() {
    return adminEmail;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getClientIp() {
    return clientIp;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getCorrelationId() {
    return correlationId;
  }
}
