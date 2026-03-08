package com.fix.channel.service;

import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.vo.MemberPasswordUpdateCommand;
import com.fix.channel.vo.MemberProfileResult;
import com.fix.channel.vo.MemberProfileUpdateCommand;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

  private final MemberRepository memberRepository;
  private final AuditLogRepository auditLogRepository;
  private final PasswordEncoder passwordEncoder;
  @SuppressWarnings("rawtypes")
  private final ObjectProvider<FindByIndexNameSessionRepository> sessionRepositoryProvider;

  @Transactional(readOnly = true)
  public MemberProfileResult readMyProfile(HttpServletRequest request) {
    Member member = requireAuthenticatedMember(request);
    return toProfileResult(member);
  }

  @Transactional
  public MemberProfileResult updateMyProfile(MemberProfileUpdateCommand command, HttpServletRequest request) {
    Member member = requireAuthenticatedMember(request);

    String beforeName = member.getName();
    String updatedName = command.getName().trim();
    member.updateProfileName(updatedName);

    auditLogRepository.save(AuditLog.of(
        member.getId(),
        "MEMBER_PROFILE_UPDATE",
        "MEMBER",
        String.valueOf(member.getId()),
        "beforeName=" + beforeName + ", afterName=" + updatedName
    ));

    return toProfileResult(member);
  }

  @Transactional
  public void updateMyPassword(MemberPasswordUpdateCommand command, HttpServletRequest request) {
    Member member = requireAuthenticatedMember(request);

    if (!passwordEncoder.matches(command.getCurrentPassword(), member.getPasswordHash())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "current password mismatch");
    }

    member.updatePasswordHash(passwordEncoder.encode(command.getNewPassword()));

    auditLogRepository.save(AuditLog.of(
        member.getId(),
        "MEMBER_PASSWORD_UPDATE",
        "MEMBER",
        String.valueOf(member.getId()),
        "password changed"
    ));

    invalidateAllMemberSessions(member.getEmail());
    HttpSession currentSession = request.getSession(false);
    if (currentSession != null) {
      currentSession.invalidate();
    }
    SecurityContextHolder.clearContext();
  }

  private Member requireAuthenticatedMember(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "authentication required");
    }

    Object memberIdAttr = session.getAttribute("AUTH_MEMBER_ID");
    if (!(memberIdAttr instanceof Number memberIdNumber)) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "authentication required");
    }

    Long memberId = memberIdNumber.longValue();
    return memberRepository.findById(memberId)
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "authentication required"));
  }

  private MemberProfileResult toProfileResult(Member member) {
    return MemberProfileResult.of(
        member.getId(),
        member.getEmail(),
        member.getName(),
        member.getRole(),
        member.getCreatedAt()
    );
  }

  private void invalidateAllMemberSessions(String email) {
    @SuppressWarnings("rawtypes")
    FindByIndexNameSessionRepository sessionRepository = sessionRepositoryProvider.getIfAvailable();
    if (sessionRepository == null) {
      return;
    }

    @SuppressWarnings("unchecked")
    Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(email);
    sessions.keySet().forEach(sessionRepository::deleteById);
  }
}
