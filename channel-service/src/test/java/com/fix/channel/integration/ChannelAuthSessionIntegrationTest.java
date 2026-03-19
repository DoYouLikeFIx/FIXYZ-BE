package com.fix.channel.integration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.client.CorebankLinkedAccountProfile;
import com.fix.channel.client.CorebankProvisioningClient;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.service.TotpService;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class ChannelAuthSessionIntegrationTest extends ChannelContainersIntegrationTestBase {

  private static final long TOTP_CODE_ROTATION_TIMEOUT_MS = 31_000L;
  private static final long TOTP_CODE_ROTATION_POLL_MS = 100L;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private AuditLogRepository auditLogRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private SessionRepository<? extends Session> sessionRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private TotpService totpService;

  @MockitoBean
  private CorebankProvisioningClient corebankProvisioningClient;

  @BeforeEach
  void setUp() {
    memberRepository.deleteAll();
    auditLogRepository.deleteAll();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });

    when(corebankProvisioningClient.provisionDefaultAccount(any(), any(), any(), any()))
        .thenReturn(new CorebankLinkedAccountProfile(1001L, 1L, "110123456789"));
  }

  @Test
  void shouldRegisterAndIssueRedisBackedSessionCookieOnLogin() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .with(csrf())
            .param("email", "it.user@fixyz.com")
            .param("password", "Abcd1234!")
            .param("name", "IT User"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("it.user@fixyz.com"));

    Member saved = memberRepository.findByEmail("it.user@fixyz.com").orElseThrow();
    saved.enableTotpEnrollment();
    memberRepository.saveAndFlush(saved);
    totpService.provisionActiveSecret(saved);

    PreAuthSession preAuthSession = bootstrapPreAuthSession();

    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .cookie(preAuthSession.sessionCookie())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .param("email", "it.user@fixyz.com")
            .param("password", "Abcd1234!"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nextAction").value("VERIFY_TOTP"))
        .andReturn();

    String loginToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
        .path("data")
        .path("loginToken")
        .asText();

    TotpLoginVerification verification = verifyTotpAndGetSession(saved, preAuthSession, loginToken);
    String loginSessionId = verification.sessionId();

    Session persisted = sessionRepository.findById(loginSessionId);
    assertThat(persisted).isNotNull();
    Object memberIdAttr = persisted.getAttribute("AUTH_MEMBER_ID");
    Object memberNameAttr = persisted.getAttribute("AUTH_MEMBER_NAME");
    assertThat(memberIdAttr).isEqualTo(saved.getId());
    assertThat(memberNameAttr).isEqualTo("IT User");
  }

  @Test
  void shouldInvalidatePreviousSessionWhenSameAccountLogsInAgain() throws Exception {
    memberRepository.save(
        Member.registerUser("M-IT-LOGIN-001", "same.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Same User")
    );

    String firstSessionId = loginAndGetSessionId("same.user@fixyz.com", "Abcd1234!");
    String secondSessionId = loginAndGetSessionId("same.user@fixyz.com", "Abcd1234!");

    assertThat(secondSessionId).isNotEqualTo(firstSessionId);

    mockMvc.perform(get("/api/v1/notifications/stream")
            .cookie(new Cookie("SESSION", firstSessionId)))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.code").value("CHANNEL-001"))
        .andExpect(jsonPath("$.message").value("channel session expired"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications/stream"));

    mockMvc.perform(get("/api/v1/notifications/stream")
            .cookie(new Cookie("SESSION", secondSessionId)))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted())
        .andExpect(result -> {
          String contentType = result.getResponse().getContentType();
          if (contentType != null) {
            assertThat(contentType).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
          }
        });
  }

  @Test
  void shouldReturnUnauthorizedEnvelopeWhenSessionCookieMissingOnNotificationsStream() throws Exception {
    mockMvc.perform(get("/api/v1/notifications/stream"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication required"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications/stream"));
  }

  @Test
  void shouldLogoutAndExpireSessionCookieImmediately() throws Exception {
    Member saved = memberRepository.save(
        Member.registerUser("M-IT-LOGOUT-001", "logout.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Logout User")
    );

    String sessionId = loginAndGetSessionId("logout.user@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    mockMvc.perform(post("/api/v1/auth/logout")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Set-Cookie", containsString("SESSION=")))
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.message").value("logout completed"));

    assertThat(sessionRepository.findById(sessionId)).isNull();

    mockMvc.perform(get("/api/v1/notifications/stream")
            .cookie(new Cookie("SESSION", sessionId)))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.code").value("CHANNEL-001"))
        .andExpect(jsonPath("$.message").value("channel session expired"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications/stream"));

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getMemberId()).isEqualTo(saved.getId());
          assertThat(log.getAction()).isEqualTo("LOGOUT");
          assertThat(log.getTargetType()).isEqualTo("SESSION");
          assertThat(log.getTargetId()).isEqualTo("[REDACTED]");
          assertThat(log.getIpAddress()).isNotBlank();
          assertThat(log.getUserAgent()).isNotBlank();
          assertThat(log.getCorrelationId()).isNotBlank();
        });
  }

  @Test
  void shouldRejectLogoutWhenSessionMemberIdMissing() throws Exception {
    memberRepository.save(
        Member.registerUser("M-IT-LOGOUT-002", "logout.missing@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Logout Missing")
    );

    String sessionId = loginAndGetSessionId("logout.missing@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    Session persisted = sessionRepository.findById(sessionId);
    assertThat(persisted).isNotNull();
    persisted.removeAttribute("AUTH_MEMBER_ID");
    saveSession(persisted);

    mockMvc.perform(post("/api/v1/auth/logout")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication required"))
        .andExpect(jsonPath("$.path").value("/api/v1/auth/logout"));

    assertThat(auditLogRepository.findAll())
        .noneSatisfy(log -> assertThat(log.getAction()).isEqualTo("LOGOUT"));
  }

  @Test
  void shouldReturnSameUnauthorizedEnvelopeForWrongPasswordAndUnknownEmail() throws Exception {
    memberRepository.save(
        Member.registerUser("M-IT-LOGIN-002", "known.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Known User")
    );

    mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", "known.user@fixyz.com")
            .param("password", "Wrong1234!"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_001"))
        .andExpect(jsonPath("$.message").value("invalid credentials"));

    mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", "unknown.user@fixyz.com")
            .param("password", "Wrong1234!"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_001"))
        .andExpect(jsonPath("$.message").value("invalid credentials"));
  }

  @Test
  void shouldRejectDuplicateRegistrationEmail() throws Exception {
    memberRepository.save(
        Member.registerUser("M-IT-REG-001", "dup.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Dup User")
    );

    mockMvc.perform(post("/api/v1/auth/register")
            .with(csrf())
            .param("email", "DUP.USER@fixyz.com")
            .param("password", "Abcd1234!")
            .param("name", "Another User"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("member already exists"));
  }

  @Test
  void shouldReturnCurrentSessionProfileWhenAuthenticated() throws Exception {
    Member saved = memberRepository.save(
        Member.registerUser("M-IT-SESSION-001", "session.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Session User")
    );

    String sessionId = loginAndGetSessionId("session.user@fixyz.com", "Abcd1234!");

    mockMvc.perform(get("/api/v1/auth/session")
            .cookie(new Cookie("SESSION", sessionId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.memberUuid").value(saved.getMemberNo()))
        .andExpect(jsonPath("$.data.username").value("session.user"))
        .andExpect(jsonPath("$.data.email").value("session.user@fixyz.com"))
        .andExpect(jsonPath("$.data.name").value("Session User"))
        .andExpect(jsonPath("$.data.role").value("ROLE_USER"))
        .andExpect(jsonPath("$.data.totpEnrolled").value(true));
  }

  @Test
  void shouldReturnUnauthorizedEnvelopeWhenSessionCookieMissingOnSessionEndpoint() throws Exception {
    mockMvc.perform(get("/api/v1/auth/session"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication required"))
        .andExpect(jsonPath("$.path").value("/api/v1/auth/session"));
  }

  @Test
  void shouldReadMyProfileWhenAuthenticated() throws Exception {
    Member saved = memberRepository.save(
        Member.registerUser("M-IT-PROFILE-001", "profile.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Profile User")
    );
    String sessionId = loginAndGetSessionId("profile.user@fixyz.com", "Abcd1234!");

    mockMvc.perform(get("/api/v1/members/me")
            .cookie(new Cookie("SESSION", sessionId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.memberId").value(saved.getId()))
        .andExpect(jsonPath("$.data.email").value("profile.user@fixyz.com"))
        .andExpect(jsonPath("$.data.name").value("Profile User"))
        .andExpect(jsonPath("$.data.role").value("ROLE_USER"));
  }

  @Test
  void shouldRequireAuthenticationForMemberProfileEndpoint() throws Exception {
    mockMvc.perform(get("/api/v1/members/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication required"))
        .andExpect(jsonPath("$.path").value("/api/v1/members/me"));
  }

  @Test
  void shouldUpdateMyProfileAndPersistAuditTrail() throws Exception {
    Member saved = memberRepository.save(
        Member.registerUser("M-IT-PROFILE-002", "profile.update@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Old Name")
    );
    String sessionId = loginAndGetSessionId("profile.update@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    mockMvc.perform(patch("/api/v1/members/me")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .param("name", "New Name"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.memberId").value(saved.getId()))
        .andExpect(jsonPath("$.data.name").value("New Name"));

    Member updated = memberRepository.findById(saved.getId()).orElseThrow();
    assertThat(updated.getName()).isEqualTo("New Name");

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getMemberId()).isEqualTo(saved.getId());
          assertThat(log.getAction()).isEqualTo("MEMBER_PROFILE_UPDATE");
          assertThat(log.getTargetType()).isEqualTo("MEMBER");
          assertThat(log.getTargetId()).isEqualTo(String.valueOf(saved.getId()));
          assertThat(log.getDetail()).contains("beforeName=Old Name", "afterName=New Name");
          assertThat(log.getIpAddress()).isNotBlank();
          assertThat(log.getUserAgent()).isNotBlank();
          assertThat(log.getCorrelationId()).isNotBlank();
        });
  }

  @Test
  void shouldRejectProfileUpdateWhenNameValidationFails() throws Exception {
    memberRepository.save(
        Member.registerUser("M-IT-PROFILE-003", "profile.invalid@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Valid Name")
    );
    String sessionId = loginAndGetSessionId("profile.invalid@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    mockMvc.perform(patch("/api/v1/members/me")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .param("name", "A"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  void shouldRejectProfileUpdateWhenTrimmedNameValidationFails() throws Exception {
    memberRepository.save(
        Member.registerUser("M-IT-PROFILE-004", "profile.trim@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Trim User")
    );
    String sessionId = loginAndGetSessionId("profile.trim@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    mockMvc.perform(patch("/api/v1/members/me")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .param("name", " A "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  void shouldChangePasswordAndInvalidateCurrentSession() throws Exception {
    Member saved = memberRepository.save(
        Member.registerUser("M-IT-PW-001", "pw.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Pw User")
    );
    String sessionId = loginAndGetSessionId("pw.user@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    mockMvc.perform(patch("/api/v1/members/me/password")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .param("currentPassword", "Abcd1234!")
            .param("newPassword", "Qwer1234!"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    Member updated = memberRepository.findById(saved.getId()).orElseThrow();
    assertThat(passwordEncoder.matches("Qwer1234!", updated.getPasswordHash())).isTrue();
    assertThat(passwordEncoder.matches("Abcd1234!", updated.getPasswordHash())).isFalse();

    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getMemberId()).isEqualTo(saved.getId());
          assertThat(log.getAction()).isEqualTo("MEMBER_PASSWORD_UPDATE");
          assertThat(log.getTargetType()).isEqualTo("MEMBER");
          assertThat(log.getTargetId()).isEqualTo(String.valueOf(saved.getId()));
          assertThat(log.getDetail()).isEqualTo("password changed");
          assertThat(log.getIpAddress()).isNotBlank();
          assertThat(log.getUserAgent()).isNotBlank();
          assertThat(log.getCorrelationId()).isNotBlank();
        });

    mockMvc.perform(get("/api/v1/auth/session")
            .cookie(new Cookie("SESSION", sessionId)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-016"))
        .andExpect(jsonPath("$.message").value("stale session after password change"));

    mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", "pw.user@fixyz.com")
            .param("password", "Abcd1234!"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_001"))
        .andExpect(jsonPath("$.message").value("invalid credentials"));

    mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", "pw.user@fixyz.com")
            .param("password", "Qwer1234!"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void shouldRejectPasswordChangeWhenCurrentPasswordMismatch() throws Exception {
    memberRepository.save(
        Member.registerUser("M-IT-PW-002", "pw.mismatch@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Mismatch User")
    );
    String sessionId = loginAndGetSessionId("pw.mismatch@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    mockMvc.perform(patch("/api/v1/members/me/password")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .param("currentPassword", "Wrong1234!")
            .param("newPassword", "Qwer1234!"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"))
        .andExpect(jsonPath("$.message").value("current password mismatch"));
  }

  @Test
  void shouldRejectPasswordChangeWhenNewPasswordPolicyInvalid() throws Exception {
    memberRepository.save(
        Member.registerUser("M-IT-PW-003", "pw.policy@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Policy User")
    );
    String sessionId = loginAndGetSessionId("pw.policy@fixyz.com", "Abcd1234!");
    String csrfToken = fetchCsrfToken(sessionId);

    mockMvc.perform(patch("/api/v1/members/me/password")
            .cookie(new Cookie("SESSION", sessionId))
            .header("X-CSRF-TOKEN", csrfToken)
            .param("currentPassword", "Abcd1234!")
            .param("newPassword", "weakpw"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  private String loginAndGetSessionId(String email, String password) throws Exception {
    Member saved = memberRepository.findByEmail(email).orElseThrow();
    PreAuthSession preAuthSession = bootstrapPreAuthSession();
    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .cookie(preAuthSession.sessionCookie())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .param("email", email)
            .param("password", password))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andReturn();

    JsonNode loginData = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data");
    String nextAction = loginData.path("nextAction").asText();
    String loginToken = loginData.path("loginToken").asText();

    assertThat(nextAction).isIn("VERIFY_TOTP", "ENROLL_TOTP");
    assertThat(loginToken).isNotBlank();

    if ("VERIFY_TOTP".equals(nextAction)) {
      return verifyTotpAndGetSession(saved, preAuthSession, loginToken).sessionId();
    }

    MvcResult enrollResult = mockMvc.perform(post("/api/v1/members/me/totp/enroll")
            .cookie(preAuthSession.sessionCookie())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("loginToken", loginToken))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.enrollmentToken").isString())
        .andExpect(jsonPath("$.data.manualEntryKey").isString())
        .andReturn();

    JsonNode enrollData = objectMapper.readTree(enrollResult.getResponse().getContentAsString()).path("data");
    String enrollmentToken = enrollData.path("enrollmentToken").asText();
    String manualEntryKey = enrollData.path("manualEntryKey").asText();
    assertThat(enrollmentToken).isNotBlank();
    assertThat(manualEntryKey).isNotBlank();

    MvcResult confirmResult = mockMvc.perform(post("/api/v1/members/me/totp/confirm")
            .cookie(preAuthSession.sessionCookie())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "loginToken", loginToken,
                "enrollmentToken", enrollmentToken,
                "otpCode", totpService.currentCodeForManualEntryKey(manualEntryKey)
            ))))
        .andExpect(status().isOk())
        .andReturn();

    return extractSessionId(confirmResult);
  }

  private String json(Object payload) throws Exception {
    return objectMapper.writeValueAsString(payload);
  }

  private TotpLoginVerification verifyTotpAndGetSession(Member member, PreAuthSession preAuthSession, String loginToken)
      throws Exception {
    String initialOtp = totpService.currentCode(member);
    MvcResult initialAttempt = performTotpVerify(preAuthSession, loginToken, initialOtp);
    if (initialAttempt.getResponse().getStatus() == 200) {
      return successfulTotpLogin(member, initialAttempt);
    }

    JsonNode initialError = objectMapper.readTree(initialAttempt.getResponse().getContentAsString());
    String initialErrorCode = initialError.path("code").asText();
    if (!"AUTH-010".equals(initialErrorCode) && !"AUTH-011".equals(initialErrorCode)) {
      throw new AssertionError(
          "Unexpected otp verify response: status=" + initialAttempt.getResponse().getStatus()
              + ", code=" + initialErrorCode
              + ", body=" + initialAttempt.getResponse().getContentAsString()
      );
    }

    String rotatedOtp = waitForNextTotpCode(member, initialOtp);
    MvcResult retryAttempt = performTotpVerify(preAuthSession, loginToken, rotatedOtp);
    if (retryAttempt.getResponse().getStatus() == 200) {
      return successfulTotpLogin(member, retryAttempt);
    }

    String retryResponseBody = retryAttempt.getResponse().getContentAsString();
    JsonNode retryError = objectMapper.readTree(retryResponseBody);
    String retryErrorCode = retryError.path("code").asText();

    throw new AssertionError(
        "TOTP verify failed after retrying with a rotated code. initialErrorCode="
            + initialErrorCode + ", retryErrorCode=" + retryErrorCode
            + ", retryResponseBody=" + retryResponseBody
    );
  }

  private String waitForNextTotpCode(Member member, String previousOtp) throws InterruptedException {
    long deadline = System.currentTimeMillis() + TOTP_CODE_ROTATION_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      Thread.sleep(TOTP_CODE_ROTATION_POLL_MS);
      String candidate = totpService.currentCode(member);
      if (!candidate.equals(previousOtp)) {
        return candidate;
      }
    }
    throw new AssertionError("Timed out waiting for TOTP code rotation");
  }

  private MvcResult performTotpVerify(PreAuthSession preAuthSession, String loginToken, String otpCode) throws Exception {
    return mockMvc.perform(post("/api/v1/auth/otp/verify")
            .cookie(preAuthSession.sessionCookie())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "loginToken", loginToken,
                "otpCode", otpCode
            ))))
        .andReturn();
  }

  private TotpLoginVerification successfulTotpLogin(Member member, MvcResult verifyResult) throws Exception {
    assertSuccessfulTotpVerify(verifyResult, member);
    return new TotpLoginVerification(extractSessionId(verifyResult), verifyResult);
  }

  private void assertSuccessfulTotpVerify(MvcResult verifyResult, Member member) throws Exception {
    assertThat(verifyResult.getResponse().getStatus()).isEqualTo(200);
    assertThat(verifyResult.getResponse().getHeader("Set-Cookie")).contains("SESSION");
    assertThat(verifyResult.getResponse().getHeader("Set-Cookie")).contains("HttpOnly");
    assertThat(verifyResult.getResponse().getHeader("Set-Cookie")).contains("SameSite=strict");
    assertThat(verifyResult.getResponse().getHeader("Set-Cookie")).doesNotContain("SameSite=None");
    JsonNode responseBody = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
    assertThat(responseBody.path("success").asBoolean()).isTrue();
    assertThat(responseBody.path("data").path("memberUuid").asText()).isEqualTo(member.getMemberNo());
  }

  private String extractSessionId(MvcResult result) {
    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    String sessionId = sessionCookie.getValue();
    assertThat(sessionId).isNotBlank();
    return sessionId;
  }

  private PreAuthSession bootstrapPreAuthSession() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andReturn();

    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    String csrfToken = root.path("data").path("token").asText();
    assertThat(csrfToken).isNotBlank();
    return new PreAuthSession(sessionCookie.getValue(), csrfToken);
  }

  private String fetchCsrfToken(String sessionId) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")
            .cookie(new Cookie("SESSION", sessionId)))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    String csrfToken = root.path("data").path("token").asText();
    assertThat(csrfToken).isNotBlank();
    return csrfToken;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void saveSession(Session session) {
    ((SessionRepository) sessionRepository).save(session);
  }

  private record PreAuthSession(String sessionId, String csrfToken) {
    private Cookie sessionCookie() {
      return new Cookie("SESSION", sessionId);
    }
  }

  private record TotpLoginVerification(String sessionId, MvcResult response) {
  }
}
