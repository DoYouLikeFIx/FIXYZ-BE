package com.fix.channel.dto.response;

import com.fix.channel.vo.TotpRebindBootstrapResult;
import java.time.Instant;

public record TotpRebindBootstrapResponse(
    String rebindToken,
    String manualEntryKey,
    String qrUri,
    String enrollmentToken,
    Instant expiresAt
) {

  public static TotpRebindBootstrapResponse from(TotpRebindBootstrapResult result) {
    return new TotpRebindBootstrapResponse(
        result.getRebindToken(),
        result.getManualEntryKey(),
        result.getQrUri(),
        result.getEnrollmentToken(),
        result.getExpiresAt()
    );
  }
}
