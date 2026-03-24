package com.fix.channel.dto.response;

import com.fix.common.error.ApiResponse;
import java.time.Instant;

public final class ApiResponseAccountSummaryResponse extends ApiResponse<AccountSummaryResponse> {

  public ApiResponseAccountSummaryResponse() {
    super(true, null, null, Instant.EPOCH);
  }

  private ApiResponseAccountSummaryResponse(AccountSummaryResponse data) {
    super(true, data, null, Instant.now());
  }

  public static ApiResponseAccountSummaryResponse success(AccountSummaryResponse data) {
    return new ApiResponseAccountSummaryResponse(data);
  }
}
