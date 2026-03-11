package com.fix.channel.dto.response;

import com.fix.channel.vo.CsrfBootstrapResult;

public record CsrfBootstrapResponse(
    String token,
    String headerName,
    String parameterName,
    String sessionMode
) {

  public static CsrfBootstrapResponse from(CsrfBootstrapResult result) {
    return new CsrfBootstrapResponse(
        result.getToken(),
        result.getHeaderName(),
        result.getParameterName(),
        result.getSessionMode()
    );
  }
}
