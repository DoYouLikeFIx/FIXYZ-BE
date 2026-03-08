package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ChannelAuthSessionIntegrationTest extends ChannelContainersIntegrationTestBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private SessionRepository<? extends Session> sessionRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @BeforeEach
  void setUp() {
    memberRepository.deleteAll();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
    assertThat(sessionRepository.getClass().getName()).contains("RedisIndexedSessionRepository");
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

    MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", "it.user@fixyz.com")
            .param("password", "Abcd1234!"))
        .andExpect(status().isOk())
        .andExpect(header().string("Set-Cookie", containsString("SESSION")))
        .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
        .andExpect(header().string("Set-Cookie", containsString("SameSite=strict")))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.memberId").value(saved.getId()))
        .andReturn();

    String loginSessionId = extractSessionId(loginResult);

    Session persisted = sessionRepository.findById(loginSessionId);
    assertThat(persisted).isNotNull();
    Object memberIdAttr = persisted.getAttribute("AUTH_MEMBER_ID");
    Object memberNameAttr = persisted.getAttribute("AUTH_MEMBER_NAME");
    assertThat(memberIdAttr).isEqualTo(saved.getId());
    assertThat(memberNameAttr).isEqualTo("IT User");
  }

  @Test
  void shouldInvalidatePreviousSessionWhenSameAccountLogsInAgain() throws Exception {
    Member saved = memberRepository.save(
        Member.registerUser("M-IT-LOGIN-001", "same.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Same User")
    );

    String firstSessionId = loginAndGetSessionId("same.user@fixyz.com", "Abcd1234!");
    String secondSessionId = loginAndGetSessionId("same.user@fixyz.com", "Abcd1234!");

    assertThat(secondSessionId).isNotEqualTo(firstSessionId);
    assertThat(sessionRepository.findById(firstSessionId)).isNull();
    assertThat(sessionRepository.findById(secondSessionId)).isNotNull();

    mockMvc.perform(get("/api/v1/notifications/stream")
            .cookie(new Cookie("SESSION", firstSessionId))
            .param("memberId", String.valueOf(saved.getId())))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/v1/notifications/stream")
            .cookie(new Cookie("SESSION", secondSessionId))
            .param("memberId", String.valueOf(saved.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
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
            .cookie(new Cookie("SESSION", sessionId))
            .param("memberId", String.valueOf(saved.getId())))
        .andExpect(status().isUnauthorized());
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

  private String loginAndGetSessionId(String email, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", email)
            .param("password", password))
        .andExpect(status().isOk())
        .andReturn();

    return extractSessionId(result);
  }

  private String extractSessionId(MvcResult result) {
    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    String sessionId = sessionCookie.getValue();
    assertThat(sessionId).isNotBlank();
    return sessionId;
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
}
