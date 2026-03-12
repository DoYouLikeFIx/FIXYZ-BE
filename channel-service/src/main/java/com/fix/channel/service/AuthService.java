package com.fix.channel.service;

import com.fix.channel.client.CorebankProvisioningClient;
import com.fix.channel.client.CorebankLinkedAccountProfile;
import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Member;
import com.fix.channel.entity.SecurityEvent;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.vo.AuthLoginCommand;
import com.fix.channel.vo.AuthLoginResult;
import com.fix.channel.vo.AuthRegisterCommand;
import com.fix.channel.vo.AuthRegisterResult;
import com.fix.channel.vo.AuthSessionResult;
import com.fix.channel.vo.OtpVerifyCommand;
import com.fix.channel.vo.OtpVerifyResult;
import com.fix.channel.vo.TotpConfirmCommand;
import com.fix.channel.vo.TotpEnrollCommand;
import com.fix.channel.vo.TotpEnrollResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.web.CorrelationIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseCookie;
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
  private static final String ACCOUNT_LOCKED_EVENT_TYPE = "ACCOUNT_LOCKED";
  private static final String ACCOUNT_LOCKED_SEVERITY = "HIGH";
  private static final String AUTH_ACCOUNT_ID = "AUTH_ACCOUNT_ID";
  private static final String AUTH_LAST_MFA_VERIFIED_AT = "AUTH_LAST_MFA_VERIFIED_AT";
  private static final String NEXT_ACTION_VERIFY_TOTP = "VERIFY_TOTP";
  private static final String NEXT_ACTION_ENROLL_TOTP = "ENROLL_TOTP";

  private final MemberRepository memberRepository;
  private final AuditLogRepository auditLogRepository;
  private final SecurityEventRepository securityEventRepository;
  private final PasswordEncoder passwordEncoder;
  private final LoginIpRateLimitService loginIpRateLimitService;
  private final LoginTokenService loginTokenService;
  private final OtpVerifyRateLimitService otpVerifyRateLimitService;
  private final TotpEnrollRateLimitService totpEnrollRateLimitService;
  private final TotpReplayGuardService totpReplayGuardService;
  private final TotpService totpService;
  private final CorebankProvisioningClient corebankProvisioningClient;
  @SuppressWarnings("rawtypes")
  private final ObjectProvider<FindByIndexNameSessionRepository> sessionRepositoryProvider;

  @Value("${server.servlet.session.cookie.name:SESSION}")
  private String sessionCookieName;
  @Value("${server.servlet.session.cookie.http-only:true}")
  private boolean sessionCookieHttpOnly;
  @Value("${server.servlet.session.cookie.same-site:strict}")
  private String sessionCookieSameSite;
  @Value("${server.servlet.session.cookie.secure:false}")
  private boolean sessionCookieSecure;
  @Value("${auth.guardrails.account-lockout.max-failed-attempts:5}")
  private int accountLockoutMaxFailedAttempts;
  @Value("${auth.demo.auto-totp-enrolled:false}")
  private boolean demoAutoTotpEnrolled;

  @Transactional
  public AuthRegisterResult register(AuthRegisterCommand command, String correlationId) {
    String email = normalizeEmail(command.getEmail());

    Member member = Member.registerUser(
        nextMemberNo(),
        email,
        passwordEncoder.encode(command.getPassword()),
        command.getName().trim()
    );
    if (demoAutoTotpEnrolled) {
      member.enableTotpEnrollment();
    }
    Member saved;
    try {
      saved = memberRepository.saveAndFlush(member);
    } catch (DataIntegrityViolationException ex) {
      if (isDuplicateMemberEmail(ex)) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "member already exists");
      }
      throw ex;
    }

    CorebankLinkedAccountProfile linkedAccountProfile = corebankProvisioningClient.provisionDefaultAccount(
        saved.getId(),
        saved.getMemberNo(),
        saved.getEmail(),
        correlationId
    );
    if (linkedAccountProfile != null) {
      saved.updateLinkedAccount(linkedAccountProfile.accountId(), linkedAccountProfile.accountNumber());
    }
    if (demoAutoTotpEnrolled && !totpService.hasActiveSecret(saved)) {
      totpService.provisionActiveSecret(saved);
    }

    auditLogRepository.save(AuditLog.of(
        saved.getId(),
        AuditAction.AUTH_REGISTER,
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

  @Transactional(noRollbackFor = BusinessException.class)
  public AuthLoginResult login(AuthLoginCommand command, HttpServletRequest request) {
    String email = normalizeEmail(command.getEmail());
    String clientIp = resolveClientIp(request);
    String userAgent = resolveUserAgent(request);

    Member member = memberRepository.findByEmailForUpdate(email).orElse(null);
    if (member != null && member.isLocked()) {
      auditLogRepository.save(AuditLog.of(
          member.getId(),
          AuditAction.AUTH_LOGIN_FAILURE,
          "MEMBER",
          String.valueOf(member.getId()),
          "email=" + email + ", reason=account_locked"
      ));
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED, "account locked");
    }

    if (loginIpRateLimitService.isBlocked(clientIp)) {
      throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "rate limit exceeded");
    }

    if (member == null) {
      loginIpRateLimitService.recordFailure(clientIp);
      auditLogRepository.save(AuditLog.of(
          null,
          AuditAction.AUTH_LOGIN_FAILURE,
          "MEMBER",
          null,
          "email=" + email
      ));
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "invalid credentials");
    }

    boolean matched = passwordEncoder.matches(command.getPassword(), member.getPasswordHash());
    boolean active = ACTIVE_STATUS.equals(member.getStatus());
    if (!matched || !active) {
      loginIpRateLimitService.recordFailure(clientIp);
      int failedAttempts = member.increaseFailedLoginAttempts();
      if (failedAttempts >= lockoutThreshold()) {
        member.lock();
        securityEventRepository.save(SecurityEvent.of(
            member.getId(),
            ACCOUNT_LOCKED_EVENT_TYPE,
            clientIp,
            userAgent,
            ACCOUNT_LOCKED_SEVERITY
        ));
        auditLogRepository.save(AuditLog.of(
            member.getId(),
            AuditAction.AUTH_LOGIN_FAILURE,
            "MEMBER",
            String.valueOf(member.getId()),
            "email=" + email + ", failedAttempts=" + failedAttempts + ", reason=account_locked"
        ));
        throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED, "account locked");
      }

      auditLogRepository.save(AuditLog.of(
          member.getId(),
          AuditAction.AUTH_LOGIN_FAILURE,
          "MEMBER",
          String.valueOf(member.getId()),
          "email=" + email
      ));
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "invalid credentials");
    }

    member.resetFailedLoginAttempts();

    LoginTokenService.LoginTokenState loginTokenState = loginTokenService.issue(member, request, clientIp, userAgent);
    String nextAction = member.isTotpEnabled() ? NEXT_ACTION_VERIFY_TOTP : NEXT_ACTION_ENROLL_TOTP;

    return AuthLoginResult.of(
        loginTokenState.loginToken(),
        nextAction,
        loginTokenState.totpEnrolled(),
        loginTokenState.expiresAt()
    );
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public TotpEnrollResult enrollTotp(TotpEnrollCommand command, HttpServletRequest request) {
    String clientIp = resolveClientIp(request);
    String userAgent = resolveUserAgent(request);
    String correlationId = resolveCorrelationId(request);
    LoginTokenService.LoginTokenState loginTokenState = loginTokenService.requireBoundActive(
        command.getLoginToken(),
        request,
        clientIp,
        userAgent
    );
    totpEnrollRateLimitService.checkAllowed(command.getLoginToken());
    totpEnrollRateLimitService.recordAttempt(command.getLoginToken());
    Member member = memberRepository.findById(loginTokenState.memberId())
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_LOGIN_TOKEN_EXPIRED, "login token expired or invalid"));
    if (member.isTotpEnabled()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "totp already enrolled");
    }

    TotpService.TotpEnrollment enrollment = totpService.bootstrap(
        member,
        loginTokenState.expiresAt(),
        command.getLoginToken()
    );
    auditLogRepository.save(AuditLog.of(
        member.getId(),
        AuditAction.AUTH_TOTP_ENROLLMENT_BOOTSTRAP,
        "TOTP",
        member.getMemberNo(),
        "totp enrollment bootstrap issued",
        clientIp,
        userAgent,
        correlationId
    ));
    return TotpEnrollResult.of(
        enrollment.manualEntryKey(),
        enrollment.qrUri(),
        enrollment.enrollmentToken(),
        enrollment.expiresAt()
    );
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public OtpVerifyResult confirmTotp(TotpConfirmCommand command, HttpServletRequest request) {
    return loginTokenService.withTokenLock(command.getLoginToken(), () -> doConfirmTotp(command, request));
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public OtpVerifyResult verifyOtp(OtpVerifyCommand command, HttpServletRequest request) {
    return loginTokenService.withTokenLock(command.getLoginToken(), () -> doVerifyOtp(command, request));
  }

  @Transactional
  public AuthSessionResult currentSession(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }

    Object memberIdAttr = session.getAttribute("AUTH_MEMBER_ID");
    if (!(memberIdAttr instanceof Number memberIdNumber)) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }

    Long memberId = memberIdNumber.longValue();
    Member member = memberRepository.findById(memberId)
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required"));
    Member resolvedMember = ensureLinkedAccount(member, CorrelationIdSupport.ensureCorrelationId(request));
    String resolvedAccountId = resolveSessionAccountId(session, resolvedMember, request);
    if (resolvedAccountId == null && resolvedMember.getAccountId() != null) {
      resolvedAccountId = String.valueOf(resolvedMember.getAccountId());
    }

    return AuthSessionResult.of(
        resolvedMember.getMemberNo(),
        resolveUsername(resolvedMember.getEmail()),
        resolvedMember.getEmail(),
        resolvedMember.getName(),
        resolvedMember.getRole(),
        resolvedMember.isTotpEnabled(),
        resolvedAccountId,
        resolvedMember.getAccountNumber()
    );
  }

  public ResponseCookie logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }

    Long memberId = extractMemberId(session);
    if (memberId == null) {
      throw new BusinessException(ErrorCode.AUTH_REQUIRED, "authentication required");
    }

    auditLogRepository.save(AuditLog.of(
        memberId,
        AuditAction.LOGOUT,
        "SESSION",
        session.getId(),
        "logout completed",
        resolveClientIp(request),
        resolveUserAgent(request),
        resolveCorrelationId(request)
    ));

    session.invalidate();
    SecurityContextHolder.clearContext();

    return expiredSessionCookie();
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private Member ensureLinkedAccount(Member member, String correlationId) {
    if (member.getAccountId() != null && member.getAccountNumber() != null && !member.getAccountNumber().isBlank()) {
      return member;
    }

    CorebankLinkedAccountProfile linkedAccountProfile =
        corebankProvisioningClient.fetchDefaultAccountProfile(member.getId(), correlationId);
    if (linkedAccountProfile == null) {
      return member;
    }
    member.updateLinkedAccount(linkedAccountProfile.accountId(), linkedAccountProfile.accountNumber());
    return member;
  }

  private boolean isDuplicateMemberEmail(DataIntegrityViolationException ex) {
    Throwable current = ex;
    while (current != null) {
      if (current instanceof ConstraintViolationException constraintViolation) {
        if (containsIgnoreCase(constraintViolation.getConstraintName(), "uk_members_email")) {
          return true;
        }
        SQLException sqlException = constraintViolation.getSQLException();
        if (isDuplicateSqlException(sqlException)) {
          return true;
        }
      }

      if (current instanceof SQLIntegrityConstraintViolationException sqlIntegrity) {
        if (isDuplicateSqlException(sqlIntegrity)) {
          return true;
        }
      }

      if (current instanceof SQLException sqlException) {
        if (isDuplicateSqlException(sqlException)) {
          return true;
        }
      }

      current = current.getCause();
    }

    return containsIgnoreCase(ex.getMessage(), "uk_members_email")
        || containsIgnoreCase(ex.getMessage(), "duplicate")
        || containsIgnoreCase(ex.getMessage(), "members.email");
  }

  private boolean containsIgnoreCase(String text, String token) {
    if (text == null || token == null) {
      return false;
    }
    return text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
  }

  private boolean isDuplicateSqlException(SQLException ex) {
    if (ex == null) {
      return false;
    }

    if ("23505".equals(ex.getSQLState())) {
      return true;
    }

    if (ex.getErrorCode() == 1062) {
      return true;
    }

    return "23000".equals(ex.getSQLState())
        && containsIgnoreCase(ex.getMessage(), "duplicate");
  }

  private OtpVerifyResult doVerifyOtp(OtpVerifyCommand command, HttpServletRequest request) {
    String correlationId = resolveCorrelationId(request);
    String clientIp = resolveClientIp(request);
    String userAgent = resolveUserAgent(request);
    LoginTokenService.LoginTokenState loginTokenState = loginTokenService.requireBoundActive(
        command.getLoginToken(),
        request,
        clientIp,
        userAgent
    );
    otpVerifyRateLimitService.checkAllowed(command.getLoginToken());

    Member member = memberRepository.findByIdForUpdate(loginTokenState.memberId())
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_LOGIN_TOKEN_EXPIRED, "login token expired or invalid"));

    if (member.isLocked()) {
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED, "account locked");
    }
    if (!member.isTotpEnabled()) {
      throw new BusinessException(
          ErrorCode.AUTH_TOTP_ENROLLMENT_REQUIRED,
          "totp enrollment required",
          new ErrorMetadata(null, null, Map.of("enrollUrl", "/settings/totp/enroll"))
      );
    }
    TotpService.TotpVerification verification = totpService.verifyCurrentCode(member, command.getOtpCode());
    if (!verification.matched()) {
      otpVerifyRateLimitService.recordFailure(command.getLoginToken());
      throw invalidOtp(member.getId(), clientIp, userAgent);
    }

    totpReplayGuardService.claim(member.getId(), verification.windowIndex(), verification.normalizedOtp());
    otpVerifyRateLimitService.clear(command.getLoginToken());
    return completeAuthenticatedLogin(member, request, correlationId, clientIp, userAgent, command.getLoginToken());
  }

  private OtpVerifyResult doConfirmTotp(TotpConfirmCommand command, HttpServletRequest request) {
    String correlationId = resolveCorrelationId(request);
    String clientIp = resolveClientIp(request);
    String userAgent = resolveUserAgent(request);
    LoginTokenService.LoginTokenState loginTokenState = loginTokenService.requireBoundActive(
        command.getLoginToken(),
        request,
        clientIp,
        userAgent
    );
    otpVerifyRateLimitService.checkAllowed(command.getLoginToken());

    Member member = memberRepository.findByIdForUpdate(loginTokenState.memberId())
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_LOGIN_TOKEN_EXPIRED, "login token expired or invalid"));

    if (member.isLocked()) {
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED, "account locked");
    }
    if (member.isTotpEnabled()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "totp already enrolled");
    }
    if (!totpService.isValidEnrollmentToken(command.getLoginToken(), command.getEnrollmentToken())) {
      throw new BusinessException(ErrorCode.AUTH_LOGIN_TOKEN_EXPIRED, "login token expired or invalid");
    }
    TotpService.TotpVerification verification = totpService.verifyPendingCode(member, command.getLoginToken(), command.getOtpCode());
    if (!verification.matched()) {
      otpVerifyRateLimitService.recordFailure(command.getLoginToken());
      throw invalidOtp(member.getId(), clientIp, userAgent);
    }

    totpReplayGuardService.claim(member.getId(), verification.windowIndex(), verification.normalizedOtp());
    totpService.promotePendingSecret(member, command.getLoginToken());
    member.enableTotpEnrollment();
    otpVerifyRateLimitService.clear(command.getLoginToken());
    auditLogRepository.save(AuditLog.of(
        member.getId(),
        AuditAction.AUTH_TOTP_ENROLLMENT_CONFIRMED,
        "TOTP",
        member.getMemberNo(),
        "totp enrollment confirmed",
        clientIp,
        userAgent,
        correlationId
    ));
    return completeAuthenticatedLogin(member, request, correlationId, clientIp, userAgent, command.getLoginToken());
  }

  private OtpVerifyResult completeAuthenticatedLogin(
      Member member,
      HttpServletRequest request,
      String correlationId,
      String clientIp,
      String userAgent,
      String loginToken
  ) {
    Instant mfaVerifiedAt = Instant.now();
    HttpSession session = establishAuthenticatedSession(member, request, correlationId, mfaVerifiedAt);

    auditLogRepository.save(AuditLog.of(
        member.getId(),
        AuditAction.AUTH_LOGIN_SUCCESS,
        "SESSION",
        session.getId(),
        "email=" + member.getEmail(),
        clientIp,
        userAgent,
        correlationId
    ));

    String accountId = resolveSessionAccountId(session, member, request);
    if (accountId == null && member.getAccountId() != null) {
      accountId = String.valueOf(member.getAccountId());
    }

    loginTokenService.consume(loginToken);
    return OtpVerifyResult.verified(
        member.getMemberNo(),
        member.getEmail(),
        member.getName(),
        member.getRole(),
        member.isTotpEnabled(),
        accountId,
        member.getAccountNumber(),
        mfaVerifiedAt
    );
  }

  private HttpSession establishAuthenticatedSession(
      Member member,
      HttpServletRequest request,
      String correlationId,
      Instant mfaVerifiedAt
  ) {
    HttpSession existingSession = request.getSession(false);
    if (existingSession != null) {
      existingSession.invalidate();
    }

    HttpSession session = request.getSession(true);
    session.setAttribute("AUTH_MEMBER_ID", member.getId());
    session.setAttribute("AUTH_MEMBER_NAME", member.getName());
    session.setAttribute(AUTH_LAST_MFA_VERIFIED_AT, mfaVerifiedAt.toString());
    session.setAttribute(
        FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
        member.getEmail()
    );
    hydrateSessionAccountId(session, member, correlationId);

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
        member.getEmail(),
        null,
        List.of(new SimpleGrantedAuthority(member.getRole()))
    ));
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

    expireOtherSessions(member.getEmail(), session.getId());
    return session;
  }

  private BusinessException invalidOtp(Long memberId, String clientIp, String userAgent) {
    securityEventRepository.save(SecurityEvent.of(
        memberId,
        "OTP_VERIFY_FAILED",
        clientIp,
        userAgent,
        "MEDIUM"
    ));
    return new BusinessException(ErrorCode.AUTH_OTP_INVALID, "otp code mismatch");
  }

  private ResponseCookie expiredSessionCookie() {
    return ResponseCookie.from(sessionCookieName, "")
        .path("/")
        .httpOnly(sessionCookieHttpOnly)
        .secure(sessionCookieSecure)
        .sameSite(sessionCookieSameSite)
        .maxAge(0)
        .build();
  }

  private String nextMemberNo() {
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

  private Long extractMemberId(HttpSession session) {
    Object memberIdAttr = session.getAttribute("AUTH_MEMBER_ID");
    if (memberIdAttr instanceof Number memberIdNumber) {
      return memberIdNumber.longValue();
    }
    return null;
  }

  private String resolveSessionAccountId(HttpSession session, Member member, HttpServletRequest request) {
    Object accountIdAttr = session.getAttribute(AUTH_ACCOUNT_ID);
    if (accountIdAttr instanceof Number accountIdNumber) {
      return String.valueOf(accountIdNumber.longValue());
    }
    if (accountIdAttr instanceof String accountIdText && !accountIdText.isBlank()) {
      return accountIdText;
    }

    return hydrateSessionAccountId(session, member, resolveCorrelationId(request));
  }

  private String hydrateSessionAccountId(HttpSession session, Member member, String correlationId) {
    try {
      Member resolvedMember = ensureLinkedAccount(member, correlationId);
      Long accountId = resolvedMember.getAccountId();
      if (accountId == null || accountId <= 0L) {
        CorebankLinkedAccountProfile linkedAccountProfile = corebankProvisioningClient.provisionDefaultAccount(
            member.getId(),
            member.getMemberNo(),
            member.getEmail(),
            correlationId
        );
        if (linkedAccountProfile != null) {
          resolvedMember.updateLinkedAccount(linkedAccountProfile.accountId(), linkedAccountProfile.accountNumber());
          accountId = linkedAccountProfile.accountId();
        }
      }
      if (accountId == null || accountId <= 0L) {
        session.removeAttribute(AUTH_ACCOUNT_ID);
        return null;
      }

      String resolvedAccountId = String.valueOf(accountId);
      session.setAttribute(AUTH_ACCOUNT_ID, resolvedAccountId);
      return resolvedAccountId;
    } catch (BusinessException ex) {
      session.removeAttribute(AUTH_ACCOUNT_ID);
      return null;
    }
  }

  private String resolveUsername(String email) {
    int atIndex = email.indexOf('@');
    if (atIndex > 0) {
      return email.substring(0, atIndex);
    }
    return email;
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String resolveUserAgent(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    if (userAgent == null || userAgent.isBlank()) {
      return "unknown";
    }
    return userAgent;
  }

  private String resolveCorrelationId(HttpServletRequest request) {
    return CorrelationIdSupport.ensureCorrelationId(request);
  }

  private int lockoutThreshold() {
    return Math.max(1, accountLockoutMaxFailedAttempts);
  }
}
