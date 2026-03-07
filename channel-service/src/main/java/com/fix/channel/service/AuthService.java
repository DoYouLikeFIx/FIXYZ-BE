package com.fix.channel.service;

import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.vo.AuthLoginCommand;
import com.fix.channel.vo.AuthLoginResult;
import com.fix.channel.vo.AuthRegisterCommand;
import com.fix.channel.vo.AuthRegisterResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String ACTIVE_STATUS = "ACTIVE";

  private final MemberRepository memberRepository;
  private final AuditLogRepository auditLogRepository;
  private final PasswordEncoder passwordEncoder;
  @SuppressWarnings("rawtypes")
  private final ObjectProvider<FindByIndexNameSessionRepository> sessionRepositoryProvider;

  @Transactional
  public AuthRegisterResult register(AuthRegisterCommand command) {
    String email = normalizeEmail(command.getEmail());
    if (memberRepository.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "member already exists");
    }

    Member member = Member.registerUser(
        nextMemberNo(),
        email,
        passwordEncoder.encode(command.getPassword()),
        command.getName().trim()
    );
    Member saved = memberRepository.save(member);

    auditLogRepository.save(AuditLog.of(
        saved.getId(),
        "AUTH_REGISTER",
        "MEMBER",
        String.valueOf(saved.getId()),
        "email=" + saved.getEmail()
    ));

    return AuthRegisterResult.of(
        saved.getId(),
        saved.getEmail(),
        saved.getName(),
        saved.getCreatedAt()
    );
  }

  @Transactional
  public AuthLoginResult login(AuthLoginCommand command, HttpServletRequest request) {
    String email = normalizeEmail(command.getEmail());
    Member member = memberRepository.findByEmail(email).orElse(null);

    // 계정 존재 여부가 드러나지 않도록 실패 응답을 동일하게 유지한다.
    if (member == null) {
      auditLogRepository.save(AuditLog.of(
          null,
          "AUTH_LOGIN_FAILURE",
          "MEMBER",
          null,
          "email=" + email
      ));
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "invalid credentials");
    }

    boolean matched = passwordEncoder.matches(command.getPassword(), member.getPasswordHash());
    boolean active = ACTIVE_STATUS.equals(member.getStatus());
    if (!matched || !active) {
      auditLogRepository.save(AuditLog.of(
          member.getId(),
          "AUTH_LOGIN_FAILURE",
          "MEMBER",
          String.valueOf(member.getId()),
          "email=" + email
      ));
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "invalid credentials");
    }

    auditLogRepository.save(AuditLog.of(
        member.getId(),
        "AUTH_LOGIN_SUCCESS",
        "MEMBER",
        String.valueOf(member.getId()),
        "email=" + email
    ));

    HttpSession existingSession = request.getSession(false);
    if (existingSession != null) {
      existingSession.invalidate();
    }

    HttpSession session = request.getSession(true);
    session.setMaxInactiveInterval((int) Duration.ofMinutes(30).toSeconds());
    session.setAttribute("AUTH_MEMBER_ID", member.getId());
    session.setAttribute("AUTH_MEMBER_NAME", member.getName());
    session.setAttribute(
        FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
        member.getEmail()
    );

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
        member.getEmail(),
        null,
        List.of(new SimpleGrantedAuthority(member.getRole()))
    ));
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

    // 동일 계정 재로그인 시 새 세션을 기준으로 기존 세션을 모두 만료시킨다.
    expireOtherSessions(member.getEmail(), session.getId());

    return AuthLoginResult.of(member.getId(), member.getEmail(), member.getName());
  }

  public void logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "authentication required");
    }

    session.invalidate();
    SecurityContextHolder.clearContext();
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String nextMemberNo() {
    // 기존 스캐폴딩/타 시스템 연계 호환을 위해 member_no를 계속 발급한다.
    for (int attempt = 0; attempt < 5; attempt++) {
      String candidate = "M-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
      if (memberRepository.findByMemberNo(candidate).isEmpty()) {
        return candidate;
      }
    }
    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "unable to allocate member number");
  }

  private void expireOtherSessions(String email, String currentSessionId) {
    @SuppressWarnings("rawtypes")
    FindByIndexNameSessionRepository sessionRepository = sessionRepositoryProvider.getIfAvailable();
    if (sessionRepository == null) {
      return;
    }

    @SuppressWarnings("unchecked")
    Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(email);
    sessions.keySet().stream()
        .filter(sessionId -> !sessionId.equals(currentSessionId))
        .forEach(sessionRepository::deleteById);
  }
}
