package com.fix.channel.service;

import com.fix.channel.entity.OrderSession;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class SessionOwnershipValidator {

  public OrderSession validateOwner(OrderSession session, Long memberId) {
    if (!session.ownedBy(memberId)) {
      throw new BusinessException(ErrorCode.CHANNEL_OWNERSHIP_MISMATCH, "Access denied.");
    }
    return session;
  }
}
