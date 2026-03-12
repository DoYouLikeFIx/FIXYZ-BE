package com.fix.channel.service;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.repository.MemberRepository;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionOwnershipValidator {

  private final MemberRepository memberRepository;

  public OrderSession validateOwner(OrderSession session, Long memberId) {
    if (!session.ownedBy(memberId)) {
      throw new BusinessException(ErrorCode.CHANNEL_OWNERSHIP_MISMATCH, "Access denied.");
    }
    return session;
  }

  public void validateLinkedAccount(Long memberId, Long accountId) {
    Long linkedAccountId = memberRepository.findById(memberId)
        .map(com.fix.channel.entity.Member::getAccountId)
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required"));
    if (!Objects.equals(linkedAccountId, accountId)) {
      throw new BusinessException(ErrorCode.CHANNEL_OWNERSHIP_MISMATCH, "Access denied.");
    }
  }
}
