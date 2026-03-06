package com.fix.channel.vo;

public class CsrfBootstrapResult {

  private final String token;
  private final String headerName;
  private final String parameterName;
  private final String sessionMode;

  private CsrfBootstrapResult(String token, String headerName, String parameterName, String sessionMode) {
    this.token = token;
    this.headerName = headerName;
    this.parameterName = parameterName;
    this.sessionMode = sessionMode;
  }

  public static CsrfBootstrapResult of(String token, String headerName, String parameterName, String sessionMode) {
    return new CsrfBootstrapResult(token, headerName, parameterName, sessionMode);
  }

  public String getToken() {
    return token;
  }

  public String getHeaderName() {
    return headerName;
  }

  public String getParameterName() {
    return parameterName;
  }

  public String getSessionMode() {
    return sessionMode;
  }
}
