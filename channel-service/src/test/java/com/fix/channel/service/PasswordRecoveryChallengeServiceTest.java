package com.fix.channel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryChallengeServiceTest {

  @Mock
  private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  private PasswordRecoveryChallengeService service;

  @BeforeEach
  void setUp() {
    PasswordRecoveryProperties properties = new PasswordRecoveryProperties();
    PasswordRecoveryTokenService tokenService = new PasswordRecoveryTokenService(properties);
    service = new PasswordRecoveryChallengeService(properties, tokenService, redisTemplateProvider);
  }

  @Test
  void validateAndConsume_rejects_missing_token_or_answer_with_auth_022() {
    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.validateAndConsume("demo@fix.com", "", "")
    );

    assertEquals(ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID, ex.getErrorCode());
  }
}
