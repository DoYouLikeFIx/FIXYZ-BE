package com.fix.channel.dto.response;

import java.time.Instant;

import com.fix.channel.vo.OtpVerifyResult;

public record OtpVerifyResponse(
    boolean verified,
    String memberUuid,
    String email,
    String name,
    String role,
    boolean totpEnrolled,
    String accountId,
    String accountNumber,
    Instant mfaVerifiedAt
) {

  public static OtpVerifyResponse from(OtpVerifyResult result) {
    return new OtpVerifyResponse(
        result.isVerified(),
        result.getMemberUuid(),
        result.getEmail(),
        result.getName(),
        result.getRole(),
        result.isTotpEnrolled(),
        result.getAccountId(),
        result.getAccountNumber(),
        result.getMfaVerifiedAt()
    );
  }
}
