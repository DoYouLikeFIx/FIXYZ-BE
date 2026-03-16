package com.fix.channel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.service.ChannelSessionInvalidationService;
import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.ErrorCode;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.common.web.TraceparentSupport;
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
import org.springframework.security.web.csrf.CsrfException;
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
      @Value("${server.servlet.session.cookie.name:SESSION}") String sessionCookieName,
      ChannelSessionInvalidationService channelSessionInvalidationService
  )
      throws Exception {
    http
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
        .sessionFixation(sessionFixation -> sessionFixation.changeSessionId()))
        .exceptionHandling(exceptionHandling -> exceptionHandling
            .authenticationEntryPoint((request, response, authException) -> {
              String correlationId = CorrelationIdSupport.ensureCorrelationId(request);
              String staleReason = resolveStaleSessionReason(
                  request.getCookies(),
                  sessionCookieName,
                  channelSessionInvalidationService
              );

              ErrorCode errorCode = resolveAuthErrorCode(request.getCookies(), sessionCookieName, staleReason);
              response.setStatus(errorCode.httpStatus());
              response.setContentType(MediaType.APPLICATION_JSON_VALUE);
              response.setCharacterEncoding("UTF-8");
              response.setHeader(CommonHeaders.X_CORRELATION_ID, correlationId);
              response.setHeader(CommonHeaders.TRACEPARENT, TraceparentSupport.ensureTraceparent(request));

              ApiErrorResponse body = ApiErrorResponse.from(
                  errorCode,
                  resolveAuthErrorMessage(errorCode, staleReason),
                  request.getRequestURI(),
                  correlationId
              );
              objectMapper.writeValue(response.getWriter(), body);
            })
            .accessDeniedHandler((request, response, accessDeniedException) -> {
              if (accessDeniedException instanceof CsrfException) {
                response.sendError(ErrorCode.AUTH_ACCESS_DENIED.httpStatus());
                return;
              }

              String correlationId = CorrelationIdSupport.ensureCorrelationId(request);
              ErrorCode errorCode = ErrorCode.AUTH_ACCESS_DENIED;

              response.setStatus(errorCode.httpStatus());
              response.setContentType(MediaType.APPLICATION_JSON_VALUE);
              response.setCharacterEncoding("UTF-8");
              response.setHeader(CommonHeaders.X_CORRELATION_ID, correlationId);
              response.setHeader(CommonHeaders.TRACEPARENT, TraceparentSupport.ensureTraceparent(request));

              ApiErrorResponse body = ApiErrorResponse.from(
                  errorCode,
                  errorCode.defaultMessage(),
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
                "/api/v1/auth/otp/verify",
                "/api/v1/auth/mfa-recovery/rebind",
                "/api/v1/auth/mfa-recovery/rebind/confirm",
                "/api/v1/members/me/totp/enroll",
                "/api/v1/members/me/totp/confirm",
                "/api/v1/auth/password/forgot",
                "/api/v1/auth/password/forgot/challenge",
                "/api/v1/auth/password/reset",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/actuator/health",
                "/actuator/info",
                "/actuator/prometheus"
            ).permitAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .requestMatchers("/actuator/**").hasRole("ADMIN")
            .anyRequest().authenticated());
    return http.build();
  }

  private ErrorCode resolveAuthErrorCode(
      Cookie[] cookies,
      String sessionCookieName,
      String staleReason
  ) {
    return staleReason != null
        ? ErrorCode.AUTH_STALE_SESSION
        : resolveFallbackAuthErrorCode(cookies, sessionCookieName);
  }

  private String resolveStaleSessionReason(
      Cookie[] cookies,
      String sessionCookieName,
      ChannelSessionInvalidationService channelSessionInvalidationService
  ) {
    if (cookies == null || cookies.length == 0) {
      return null;
    }

    String sessionId = Arrays.stream(cookies)
        .filter(cookie -> sessionCookieName.equals(cookie.getName())
            && cookie.getValue() != null
            && !cookie.getValue().isBlank())
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);

    return channelSessionInvalidationService.consumeStaleSessionReason(sessionId);
  }

  private ErrorCode resolveFallbackAuthErrorCode(Cookie[] cookies, String sessionCookieName) {
    if (cookies == null || cookies.length == 0) {
      return ErrorCode.AUTH_REQUIRED;
    }

    String sessionId = Arrays.stream(cookies)
        .filter(cookie -> sessionCookieName.equals(cookie.getName())
            && cookie.getValue() != null
            && !cookie.getValue().isBlank())
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
    return sessionId != null ? ErrorCode.CHANNEL_SESSION_EXPIRED : ErrorCode.AUTH_REQUIRED;
  }

  private String resolveAuthErrorMessage(ErrorCode errorCode, String staleReason) {
    if (errorCode == ErrorCode.AUTH_STALE_SESSION) {
      if ("mfa-rebind-completed".equals(staleReason)) {
        return "stale session after mfa recovery rebind";
      }
      return "stale session after password change";
    }
    if (errorCode == ErrorCode.CHANNEL_SESSION_EXPIRED) {
      return "channel session expired";
    }
    return "authentication required";
  }
}
