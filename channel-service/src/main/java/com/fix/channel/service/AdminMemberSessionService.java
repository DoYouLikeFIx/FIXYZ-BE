package com.fix.channel.service;

import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.support.ManualReplayIdentitySupport;
import com.fix.channel.vo.AdminActorContext;
import com.fix.channel.vo.AdminSessionInvalidationResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class AdminMemberSessionService {

  private final MemberRepository memberRepository;
  private final ChannelSessionInvalidationService channelSessionInvalidationService;
  private final AuditLogService auditLogService;

  public AdminMemberSessionService(
      MemberRepository memberRepository,
      ChannelSessionInvalidationService channelSessionInvalidationService,
      AuditLogService auditLogService
  ) {
    this.memberRepository = memberRepository;
    this.channelSessionInvalidationService = channelSessionInvalidationService;
    this.auditLogService = auditLogService;
  }

  public AdminSessionInvalidationResult invalidateMemberSessions(String memberUuid, AdminActorContext actor) {
    Member targetMember = memberRepository.findByMemberNo(memberUuid)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "target member not found"));

    int invalidatedCount = channelSessionInvalidationService.invalidateAllSessionsWithCount(
        targetMember.getEmail(),
        "admin-force-logout"
    );

    auditLogService.record(AuditLog.of(
        actor.getAdminMemberId(),
        AuditAction.ADMIN_FORCE_LOGOUT,
        "MEMBER",
        targetMember.getMemberNo(),
        "targetMemberId=" + targetMember.getId() + ",adminEmail=" + actor.getAdminEmail() + ",invalidatedCount=" + invalidatedCount,
        actor.getClientIp(),
        actor.getUserAgent(),
        actor.getCorrelationId()
    ));

    String message = invalidatedCount == 0
        ? "종료할 활성 세션이 없습니다."
        : "모든 세션이 강제 종료되었습니다.";

    return AdminSessionInvalidationResult.of(targetMember.getMemberNo(), invalidatedCount, message);
  }

  public String resolveOperatorId(Long memberId) {
    return memberRepository.findById(memberId)
        .map(member -> ManualReplayIdentitySupport.operatorIdFor(member.getMemberNo()))
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required"));
  }
}
