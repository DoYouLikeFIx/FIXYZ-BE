package com.fix.channel.dto.response;

import com.fix.channel.vo.AdminSessionInvalidationResult;

public record AdminSessionInvalidationResponse(
    String memberUuid,
    int invalidatedCount,
    String message
) {

  public static AdminSessionInvalidationResponse from(AdminSessionInvalidationResult result) {
    return new AdminSessionInvalidationResponse(
        result.getMemberUuid(),
        result.getInvalidatedCount(),
        result.getMessage()
    );
  }
}
