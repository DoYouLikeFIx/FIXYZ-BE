package com.fix.channel.dto.response;

import com.fix.channel.vo.CsrfBootstrapResult;

public class CsrfBootstrapResponse {

  private final String token;
  private final String headerName;
  private final String parameterName;
  private final String sessionMode;

  private CsrfBootstrapResponse(String token, String headerName, String parameterName, String sessionMode) {
    this.token = token;
    this.headerName = headerName;
    this.parameterName = parameterName;
    this.sessionMode = sessionMode;
  }

  public static CsrfBootstrapResponse from(CsrfBootstrapResult result) {
    return new CsrfBootstrapResponse(
        result.getToken(),
        result.getHeaderName(),
        result.getParameterName(),
        result.getSessionMode()
    );
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
