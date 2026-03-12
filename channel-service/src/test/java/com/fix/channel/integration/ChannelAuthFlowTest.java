package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.client.CorebankProvisioningClient;
import com.fix.channel.client.CorebankLinkedAccountProfile;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.service.TotpService;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:channel_auth_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.session.store-type=none",
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
class ChannelAuthFlowTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private TotpService totpService;

  @MockitoBean
  private CorebankProvisioningClient corebankProvisioningClient;

  @BeforeEach
  void cleanUp() {
    memberRepository.deleteAll();
    doReturn(new CorebankLinkedAccountProfile(1001L, 1L, "110123456789")).when(corebankProvisioningClient)
        .provisionDefaultAccount(any(), any(), any(), any());
  }

  @Test
  void shouldRegisterMemberAndPersistEncodedPassword() throws Exception {
    when(corebankProvisioningClient.provisionDefaultAccount(any(), any(), any(), any()))
        .thenReturn(new CorebankLinkedAccountProfile(1001L, 1L, "110123456789"));

    mockMvc.perform(post("/api/v1/auth/register")
            .with(csrf())
            .param("email", "NEW.USER@fixyz.com")
            .param("password", "Abcd1234!")
            .param("name", "New User"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("new.user@fixyz.com"))
        .andExpect(jsonPath("$.data.name").value("New User"));

    Member saved = memberRepository.findByEmail("new.user@fixyz.com").orElseThrow();
    assertThat(saved.getPasswordHash()).isNotEqualTo("Abcd1234!");
    assertThat(passwordEncoder.matches("Abcd1234!", saved.getPasswordHash())).isTrue();
    assertThat(saved.getRole()).isEqualTo("ROLE_USER");
    assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    assertThat(saved.getAccountId()).isEqualTo(1001L);
    assertThat(saved.getAccountNumber()).isEqualTo("110123456789");
    verify(corebankProvisioningClient).provisionDefaultAccount(
        eq(saved.getId()),
        eq(saved.getMemberNo()),
        eq(saved.getEmail()),
        anyString()
    );
  }

  @Test
  void shouldRejectDuplicateEmailRegardlessOfCase() throws Exception {
    saveMember("M-DUP-001", "dup.user@fixyz.com", "Abcd1234!", "Dup User");

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
  void shouldLoginReturnPreAuthStateAndIssueSessionAfterOtpVerification() throws Exception {
    Member member = saveMember("M-LOGIN-001", "login.user@fixyz.com", "Abcd1234!", "Login User");
    enableTotp(member);
    PreAuthSession preAuthSession = bootstrapPreAuthSession();

    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .session(preAuthSession.session())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .param("email", "LOGIN.USER@fixyz.com")
            .param("password", "Abcd1234!"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.loginToken").isString())
        .andExpect(jsonPath("$.data.nextAction").value("VERIFY_TOTP"))
        .andExpect(jsonPath("$.data.totpEnrolled").value(true))
        .andReturn();

    String loginToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
        .path("data")
        .path("loginToken")
        .asText();
    assertThat(loginToken).isNotBlank();
    assertThat(loginResult.getRequest().getSession(false)).isNotNull();

    MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
            .session(preAuthSession.session())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "loginToken", loginToken,
                "otpCode", totpService.currentCode(member)
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.verified").value(true))
        .andExpect(jsonPath("$.data.memberUuid").value(member.getMemberNo()))
        .andExpect(jsonPath("$.data.email").value("login.user@fixyz.com"))
        .andExpect(jsonPath("$.data.name").value("Login User"))
        .andReturn();

    HttpSession session = verifyResult.getRequest().getSession(false);
    assertThat(session).isNotNull();
    assertThat(session.getAttribute("AUTH_MEMBER_ID")).isEqualTo(member.getId());
    assertThat(session.getAttribute("AUTH_MEMBER_NAME")).isEqualTo("Login User");
    assertThat(session.getAttribute("AUTH_ACCOUNT_ID")).isEqualTo("1001");
  }

  @Test
  void shouldRequireTotpEnrollmentBeforeIssuingSessionForFirstLogin() throws Exception {
    Member member = saveMember("M-LOGIN-003", "enroll.user@fixyz.com", "Abcd1234!", "Enroll User");
    PreAuthSession preAuthSession = bootstrapPreAuthSession();

    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .session(preAuthSession.session())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .param("email", "enroll.user@fixyz.com")
            .param("password", "Abcd1234!"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nextAction").value("ENROLL_TOTP"))
        .andExpect(jsonPath("$.data.totpEnrolled").value(false))
        .andReturn();

    String loginToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
        .path("data")
        .path("loginToken")
        .asText();

    MvcResult enrollResult = mockMvc.perform(post("/api/v1/members/me/totp/enroll")
            .session(preAuthSession.session())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("loginToken", loginToken))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.manualEntryKey").isString())
        .andExpect(jsonPath("$.data.qrUri").isString())
        .andExpect(jsonPath("$.data.enrollmentToken").isString())
        .andReturn();

    JsonNode enrollmentData = objectMapper.readTree(enrollResult.getResponse().getContentAsString()).path("data");
    String enrollmentToken = enrollmentData.path("enrollmentToken").asText();
    String manualEntryKey = enrollmentData.path("manualEntryKey").asText();

    MvcResult confirmResult = mockMvc.perform(post("/api/v1/members/me/totp/confirm")
            .session(preAuthSession.session())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "loginToken", loginToken,
                "enrollmentToken", enrollmentToken,
                "otpCode", totpService.currentCodeForManualEntryKey(manualEntryKey)
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.verified").value(true))
        .andExpect(jsonPath("$.data.totpEnrolled").value(true))
        .andReturn();

    HttpSession session = confirmResult.getRequest().getSession(false);
    assertThat(session).isNotNull();

    Member enrolled = memberRepository.findByEmail("enroll.user@fixyz.com").orElseThrow();
    assertThat(enrolled.isTotpEnabled()).isTrue();
    assertThat(enrolled.getTotpEnrolledAt()).isNotNull();
  }

  @Test
  void shouldThrottleOtpVerificationAttemptsPerLoginToken() throws Exception {
    Member member = saveMember("M-LOGIN-004", "otp.limit@fixyz.com", "Abcd1234!", "Otp Limit");
    enableTotp(member);
    PreAuthSession preAuthSession = bootstrapPreAuthSession();

    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .session(preAuthSession.session())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .param("email", "otp.limit@fixyz.com")
            .param("password", "Abcd1234!"))
        .andExpect(status().isOk())
        .andReturn();

    String loginToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
        .path("data")
        .path("loginToken")
        .asText();

    for (int attempt = 0; attempt < 3; attempt++) {
      mockMvc.perform(post("/api/v1/auth/otp/verify")
              .session(preAuthSession.session())
              .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
              .contentType(MediaType.APPLICATION_JSON)
              .content(json(Map.of(
                  "loginToken", loginToken,
                  "otpCode", "000000"
              ))))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("AUTH-010"));
    }

    mockMvc.perform(post("/api/v1/auth/otp/verify")
            .session(preAuthSession.session())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "loginToken", loginToken,
                "otpCode", totpService.currentCode(member)
            ))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_001"))
        .andExpect(jsonPath("$.message").value("rate limit exceeded"))
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  void shouldRejectOtpVerifyFromDifferentPreAuthSession() throws Exception {
    Member member = saveMember("M-LOGIN-005", "bound.user@fixyz.com", "Abcd1234!", "Bound User");
    enableTotp(member);

    PreAuthSession loginSession = bootstrapPreAuthSession();
    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .session(loginSession.session())
            .header("X-CSRF-TOKEN", loginSession.csrfToken())
            .param("email", "bound.user@fixyz.com")
            .param("password", "Abcd1234!"))
        .andExpect(status().isOk())
        .andReturn();

    String loginToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
        .path("data")
        .path("loginToken")
        .asText();

    PreAuthSession attackerSession = bootstrapPreAuthSession();
    mockMvc.perform(post("/api/v1/auth/otp/verify")
            .session(attackerSession.session())
            .header("X-CSRF-TOKEN", attackerSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "loginToken", loginToken,
                "otpCode", totpService.currentCode(member)
            ))))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.code").value("AUTH-018"));
  }

  @Test
  void shouldReturnEnrollUrlWhenOtpVerifyIsCalledBeforeEnrollment() throws Exception {
    saveMember("M-LOGIN-006", "not.enrolled@fixyz.com", "Abcd1234!", "Not Enrolled");
    PreAuthSession preAuthSession = bootstrapPreAuthSession();

    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .session(preAuthSession.session())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .param("email", "not.enrolled@fixyz.com")
            .param("password", "Abcd1234!"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nextAction").value("ENROLL_TOTP"))
        .andReturn();

    String loginToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
        .path("data")
        .path("loginToken")
        .asText();

    mockMvc.perform(post("/api/v1/auth/otp/verify")
            .session(preAuthSession.session())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "loginToken", loginToken,
                "otpCode", "000000"
            ))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH-009"))
        .andExpect(jsonPath("$.enrollUrl").value("/settings/totp/enroll"));
  }

  @Test
  void shouldRejectTotpReplayWithinSameWindowAcrossSeparateLoginAttempts() throws Exception {
    Member member = saveMember("M-LOGIN-007", "replay.user@fixyz.com", "Abcd1234!", "Replay User");
    enableTotp(member);
    waitForStableTotpWindow();

    String replayCode = totpService.currentCode(member);

    PreAuthSession firstSession = bootstrapPreAuthSession();
    String firstToken = loginTokenFor(firstSession, "replay.user@fixyz.com", "Abcd1234!");
    mockMvc.perform(post("/api/v1/auth/otp/verify")
            .session(firstSession.session())
            .header("X-CSRF-TOKEN", firstSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "loginToken", firstToken,
                "otpCode", replayCode
            ))))
        .andExpect(status().isOk());

    PreAuthSession secondSession = bootstrapPreAuthSession();
    String secondToken = loginTokenFor(secondSession, "replay.user@fixyz.com", "Abcd1234!");
    mockMvc.perform(post("/api/v1/auth/otp/verify")
            .session(secondSession.session())
            .header("X-CSRF-TOKEN", secondSession.csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "loginToken", secondToken,
                "otpCode", replayCode
            ))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-011"));
  }

  @Test
  void shouldReturnUnauthorizedWhenPasswordDoesNotMatch() throws Exception {
    saveMember("M-LOGIN-002", "wrong.pw@fixyz.com", "Abcd1234!", "Wrong Pw");

    mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", "wrong.pw@fixyz.com")
            .param("password", "Abcd9999!"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_001"))
        .andExpect(jsonPath("$.message").value("invalid credentials"));
  }

  @Test
  void shouldReturnValidationErrorWhenPasswordPolicyIsNotMet() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .with(csrf())
            .param("email", "policy.fail@fixyz.com")
            .param("password", "weakpw")
            .param("name", "Policy Fail"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  void shouldRollbackRegistrationWhenCorebankProvisioningFails() throws Exception {
    doThrow(new BusinessException(ErrorCode.CORE_PROVISIONING_UNAVAILABLE, "corebank provisioning failed"))
        .when(corebankProvisioningClient)
        .provisionDefaultAccount(any(), any(), any(), any());

    mockMvc.perform(post("/api/v1/auth/register")
            .with(csrf())
            .param("email", "provision.fail@fixyz.com")
            .param("password", "Abcd1234!")
            .param("name", "Provision Fail"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CORE-001"))
        .andExpect(jsonPath("$.message").value("corebank provisioning failed"));

    assertThat(memberRepository.findByEmail("provision.fail@fixyz.com")).isEmpty();
  }

  private Member saveMember(String memberNo, String email, String rawPassword, String name) {
    return memberRepository.save(Member.registerUser(memberNo, email, passwordEncoder.encode(rawPassword), name));
  }

  private void enableTotp(Member member) {
    member.enableTotpEnrollment();
    memberRepository.saveAndFlush(member);
    totpService.provisionActiveSecret(member);
  }

  private PreAuthSession bootstrapPreAuthSession() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    HttpSession session = result.getRequest().getSession(false);
    assertThat(session).isInstanceOf(MockHttpSession.class);
    return new PreAuthSession((MockHttpSession) session, root.path("data").path("token").asText());
  }

  private String loginTokenFor(PreAuthSession preAuthSession, String email, String password) throws Exception {
    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .session(preAuthSession.session())
            .header("X-CSRF-TOKEN", preAuthSession.csrfToken())
            .param("email", email)
            .param("password", password))
        .andExpect(status().isOk())
        .andReturn();

    return objectMapper.readTree(loginResult.getResponse().getContentAsString())
        .path("data")
        .path("loginToken")
        .asText();
  }

  private String json(Object payload) throws Exception {
    return objectMapper.writeValueAsString(payload);
  }

  private void waitForStableTotpWindow() throws InterruptedException {
    long offset = Instant.now().getEpochSecond() % 30L;
    if (offset >= 25L) {
      Thread.sleep((31L - offset) * 1000L);
    }
  }

  private record PreAuthSession(MockHttpSession session, String csrfToken) {
  }
}
