package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.session.timeout=2s",
    "server.servlet.session.timeout=2s"
})
class ChannelSessionTimeoutIntegrationTest extends ChannelContainersIntegrationTestBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private SessionRepository<? extends Session> sessionRepository;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @BeforeEach
  void setUp() {
    memberRepository.deleteAll();
    stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
      connection.serverCommands().flushDb();
      return null;
    });
  }

  @Test
  void shouldTreatSessionAsExpiredAfterInactivityThreshold() throws Exception {
    memberRepository.save(
        Member.registerUser("M-IT-TIMEOUT-001", "timeout.user@fixyz.com", passwordEncoder.encode("Abcd1234!"), "Timeout User")
    );

    String sessionId = loginAndGetSessionId("timeout.user@fixyz.com", "Abcd1234!");
    assertThat(sessionRepository.findById(sessionId)).isNotNull();

    Thread.sleep(3000L);

    mockMvc.perform(get("/api/v1/auth/session")
            .cookie(new Cookie("SESSION", sessionId)))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.code").value("CHANNEL-001"))
        .andExpect(jsonPath("$.message").value("channel session expired"))
        .andExpect(jsonPath("$.path").value("/api/v1/auth/session"));
  }

  private String loginAndGetSessionId(String email, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", email)
            .param("password", password))
        .andExpect(status().isOk())
        .andReturn();

    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    assertThat(sessionCookie.getValue()).isNotBlank();
    return sessionCookie.getValue();
  }
}
