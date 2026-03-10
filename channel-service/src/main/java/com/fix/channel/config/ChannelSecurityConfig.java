package com.fix.channel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.ErrorCode;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class ChannelSecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    // Story 1.1 비밀번호 정책: BCrypt cost 12로 해시를 저장한다.
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public HttpSessionCsrfTokenRepository csrfTokenRepository() {
    HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
    repository.setHeaderName("X-CSRF-TOKEN");
    return repository;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      HttpSessionCsrfTokenRepository tokenRepository,
      ObjectMapper objectMapper,
      @Value("${server.servlet.session.cookie.name:SESSION}") String sessionCookieName
  )
      throws Exception {
    http
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
        .sessionFixation(sessionFixation -> sessionFixation.changeSessionId()))
        .exceptionHandling(exceptionHandling -> exceptionHandling
            .authenticationEntryPoint((request, response, authException) -> {
              String correlationId = CorrelationIdSupport.ensureCorrelationId(request);

              ErrorCode errorCode = resolveAuthErrorCode(request.getCookies(), sessionCookieName);
              response.setStatus(errorCode.httpStatus());
              response.setContentType(MediaType.APPLICATION_JSON_VALUE);
              response.setCharacterEncoding("UTF-8");
              response.setHeader(CommonHeaders.X_CORRELATION_ID, correlationId);

              ApiErrorResponse body = ApiErrorResponse.from(
                  errorCode,
                  resolveAuthErrorMessage(errorCode),
                  request.getRequestURI(),
                  correlationId
              );
              objectMapper.writeValue(response.getWriter(), body);
            }))
        .csrf(csrf -> csrf.csrfTokenRepository(tokenRepository))
        .cors(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/api/v1/ping",
                "/api/v1/errors/boom",
                "/api/v1/auth/csrf",
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/actuator/health",
                "/actuator/info"
            ).permitAll()
            .anyRequest().authenticated());
    return http.build();
  }

  private ErrorCode resolveAuthErrorCode(Cookie[] cookies, String sessionCookieName) {
    if (cookies == null || cookies.length == 0) {
      return ErrorCode.AUTH_REQUIRED;
    }

    boolean hasSessionCookie = Arrays.stream(cookies)
        .anyMatch(cookie -> sessionCookieName.equals(cookie.getName())
            && cookie.getValue() != null
            && !cookie.getValue().isBlank());

    return hasSessionCookie ? ErrorCode.CHANNEL_SESSION_EXPIRED : ErrorCode.AUTH_REQUIRED;
  }

  private String resolveAuthErrorMessage(ErrorCode errorCode) {
    if (errorCode == ErrorCode.CHANNEL_SESSION_EXPIRED) {
      return "channel session expired";
    }
    return "authentication required";
  }
}
