package com.fix.channel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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
  public SecurityFilterChain securityFilterChain(HttpSecurity http, HttpSessionCsrfTokenRepository tokenRepository)
      throws Exception {
    http
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .sessionFixation(sessionFixation -> sessionFixation.changeSessionId()))
        .exceptionHandling(exceptionHandling -> exceptionHandling
            .authenticationEntryPoint((request, response, authException) ->
                response.sendError(HttpStatus.UNAUTHORIZED.value())))
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
