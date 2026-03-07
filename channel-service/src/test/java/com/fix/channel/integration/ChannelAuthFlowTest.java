package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.channel.entity.Member;
import com.fix.channel.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
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
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void cleanUp() {
    memberRepository.deleteAll();
  }

  @Test
  void shouldRegisterMemberAndPersistEncodedPassword() throws Exception {
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
  void shouldLoginAndCreateHttpSession() throws Exception {
    Member member = saveMember("M-LOGIN-001", "login.user@fixyz.com", "Abcd1234!", "Login User");

    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .param("email", "LOGIN.USER@fixyz.com")
            .param("password", "Abcd1234!"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.memberId").value(member.getId()))
        .andExpect(jsonPath("$.data.email").value("login.user@fixyz.com"))
        .andExpect(jsonPath("$.data.name").value("Login User"))
        .andReturn();

    HttpSession session = result.getRequest().getSession(false);
    assertThat(session).isNotNull();
    assertThat(session.getAttribute("AUTH_MEMBER_ID")).isEqualTo(member.getId());
    assertThat(session.getAttribute("AUTH_MEMBER_NAME")).isEqualTo("Login User");
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

  private Member saveMember(String memberNo, String email, String rawPassword, String name) {
    return memberRepository.save(Member.registerUser(memberNo, email, passwordEncoder.encode(rawPassword), name));
  }
}
