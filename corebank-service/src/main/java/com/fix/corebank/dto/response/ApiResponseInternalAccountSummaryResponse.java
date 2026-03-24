package com.fix.corebank.dto.response;

import com.fix.common.error.ApiResponse;
import java.time.Instant;

public final class ApiResponseInternalAccountSummaryResponse extends ApiResponse<InternalAccountSummaryResponse> {

  public ApiResponseInternalAccountSummaryResponse() {
    super(true, null, null, Instant.EPOCH);
  }

  private ApiResponseInternalAccountSummaryResponse(InternalAccountSummaryResponse data) {
    super(true, data, null, Instant.now());
  }

  public static ApiResponseInternalAccountSummaryResponse success(InternalAccountSummaryResponse data) {
    return new ApiResponseInternalAccountSummaryResponse(data);
  }
}
