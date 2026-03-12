package com.fix.channel.dto.response;

import com.fix.channel.vo.TotpEnrollResult;
import java.time.Instant;

public record TotpEnrollResponse(
    String manualEntryKey,
    String qrUri,
    String enrollmentToken,
    Instant expiresAt
) {

  public static TotpEnrollResponse from(TotpEnrollResult result) {
    return new TotpEnrollResponse(
        result.getManualEntryKey(),
        result.getQrUri(),
        result.getEnrollmentToken(),
        result.getExpiresAt()
    );
  }
}
