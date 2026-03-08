package com.fix.channel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.ErrorCode;
import com.fix.common.web.CommonHeaders;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
      ObjectMapper objectMapper
  )
      throws Exception {
    http
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .sessionFixation(sessionFixation -> sessionFixation.changeSessionId()))
        .exceptionHandling(exceptionHandling -> exceptionHandling
            .authenticationEntryPoint((request, response, authException) -> {
              String correlationId = request.getHeader(CommonHeaders.X_CORRELATION_ID);
              if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
              }

              response.setStatus(HttpStatus.UNAUTHORIZED.value());
              response.setContentType(MediaType.APPLICATION_JSON_VALUE);
              response.setCharacterEncoding("UTF-8");
              response.setHeader(CommonHeaders.X_CORRELATION_ID, correlationId);

              ApiErrorResponse body = ApiErrorResponse.from(
                  ErrorCode.AUTH_UNAUTHORIZED,
                  "authentication required",
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
}
