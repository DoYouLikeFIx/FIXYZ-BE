package com.fix.channel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.channel.vo.PasswordForgotChallengeResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ProofOfWorkPasswordRecoveryChallengeServiceTest {

  @Mock
  private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private PasswordRecoveryProperties properties;
  private PasswordRecoveryTokenService tokenService;
  private ProofOfWorkPasswordRecoveryChallengeService service;

  @BeforeEach
  void setUp() {
    properties = new PasswordRecoveryProperties();
    properties.getChallenge().setTtlSeconds(120);
    properties.getChallenge().setDifficultyBits(4);
    tokenService = new PasswordRecoveryTokenService(properties);
    service = new ProofOfWorkPasswordRecoveryChallengeService(
        properties,
        tokenService,
        new ObjectMapper(),
        redisTemplateProvider
    );

    lenient().when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  void issues_v2_bundle_and_accepts_a_valid_solution() throws Exception {
    MockHttpServletRequest request = requestWithSession();
    PasswordForgotChallengeResult issued = service.issue("demo@fix.com", "demo@fix.com", request);

    assertEquals(2, issued.getChallengeContractVersion());
    assertNotNull(issued.getChallengeId());
    assertTrue(issued.getChallengeToken().startsWith("v2."));
    assertEquals("proof-of-work", issued.getChallengeType());
    assertNotNull(issued.getChallengePayload());
    assertEquals("proof-of-work", issued.getChallengePayload().kind());
    assertEquals("SHA-256", issued.getChallengePayload().proofOfWork().algorithm());
    assertEquals("nonce-decimal", issued.getChallengePayload().proofOfWork().answerFormat());
    assertEquals("{seed}:{nonce}", issued.getChallengePayload().proofOfWork().inputTemplate());
    assertEquals("utf-8", issued.getChallengePayload().proofOfWork().inputEncoding());
    assertEquals(issued.getChallengePayload().proofOfWork().difficultyBits(), issued.getChallengePayload().proofOfWork().successCondition().minimum());

    String nonce = findValidNonce(
        issued.getChallengePayload().proofOfWork().seed(),
        issued.getChallengePayload().proofOfWork().difficultyBits()
    );

    when(valueOperations.get(anyString())).thenReturn(issued.getChallengeId());
    when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);

    service.validate("demo@fix.com", "demo@fix.com", issued.getChallengeToken(), nonce, request);

    verify(valueOperations).get(anyString());
    verify(redisTemplate).execute(any(), anyList(), any(), any());
  }

  @Test
  void rejects_invalid_proof_with_auth_022() {
    MockHttpServletRequest request = requestWithSession();
    PasswordForgotChallengeResult issued = service.issue("demo@fix.com", "demo@fix.com", request);

    when(valueOperations.get(anyString())).thenReturn(issued.getChallengeId());

    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.validate("demo@fix.com", "demo@fix.com", issued.getChallengeToken(), "12", request)
    );

    assertEquals(ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID, ex.getErrorCode());
  }

  @Test
  void rejects_exact_email_drift_with_auth_022() throws Exception {
    MockHttpServletRequest request = requestWithSession();
    PasswordForgotChallengeResult issued = service.issue("demo@fix.com", "demo@fix.com", request);
    String nonce = findValidNonce(
        issued.getChallengePayload().proofOfWork().seed(),
        issued.getChallengePayload().proofOfWork().difficultyBits()
    );

    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.validate("Demo@fix.com", "demo@fix.com", issued.getChallengeToken(), nonce, request)
    );

    assertEquals(ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID, ex.getErrorCode());
  }

  @Test
  void rejects_replayed_and_stale_tokens_with_auth_024() throws Exception {
    MockHttpServletRequest request = requestWithSession();
    PasswordForgotChallengeResult issued = service.issue("demo@fix.com", "demo@fix.com", request);
    String nonce = findValidNonce(
        issued.getChallengePayload().proofOfWork().seed(),
        issued.getChallengePayload().proofOfWork().difficultyBits()
    );

    when(valueOperations.get(anyString())).thenReturn(issued.getChallengeId());
    when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L, 0L);

    service.validate("demo@fix.com", "demo@fix.com", issued.getChallengeToken(), nonce, request);

    BusinessException replayed = assertThrows(
        BusinessException.class,
        () -> service.validate("demo@fix.com", "demo@fix.com", issued.getChallengeToken(), nonce, request)
    );
    assertEquals(ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_REPLAYED, replayed.getErrorCode());

    PasswordForgotChallengeResult newer = service.issue("demo@fix.com", "demo@fix.com", request);
    when(valueOperations.get(anyString())).thenReturn(newer.getChallengeId());

    BusinessException stale = assertThrows(
        BusinessException.class,
        () -> service.validate("demo@fix.com", "demo@fix.com", issued.getChallengeToken(), nonce, request)
    );
    assertEquals(ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_REPLAYED, stale.getErrorCode());
  }

  @Test
  void returns_auth_023_when_bootstrap_infrastructure_is_unavailable() {
    when(redisTemplateProvider.getIfAvailable()).thenReturn(null);

    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.issue("demo@fix.com", "demo@fix.com", requestWithSession())
    );

    assertEquals(ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_BOOTSTRAP_UNAVAILABLE, ex.getErrorCode());
  }

  @Test
  void returns_auth_025_when_verify_infrastructure_is_unavailable() throws Exception {
    MockHttpServletRequest request = requestWithSession();
    PasswordForgotChallengeResult issued = service.issue("demo@fix.com", "demo@fix.com", request);
    String nonce = findValidNonce(
        issued.getChallengePayload().proofOfWork().seed(),
        issued.getChallengePayload().proofOfWork().difficultyBits()
    );

    when(redisTemplateProvider.getIfAvailable()).thenReturn(null);

    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.validate("demo@fix.com", "demo@fix.com", issued.getChallengeToken(), nonce, request)
    );

    assertEquals(ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_VERIFY_UNAVAILABLE, ex.getErrorCode());
  }

  private MockHttpServletRequest requestWithSession() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);
    return request;
  }

  private String findValidNonce(String seed, int difficultyBits) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    for (long nonce = 0; nonce < 10_000_000L; nonce++) {
      byte[] hash = digest.digest((seed + ":" + nonce).getBytes(StandardCharsets.UTF_8));
      if (leadingZeroBits(hash) >= difficultyBits) {
        return Long.toUnsignedString(nonce);
      }
    }
    throw new IllegalStateException("Unable to find valid nonce for test");
  }

  private int leadingZeroBits(byte[] hash) {
    int count = 0;
    for (byte value : hash) {
      int unsigned = Byte.toUnsignedInt(value);
      if (unsigned == 0) {
        count += 8;
        continue;
      }
      count += Integer.numberOfLeadingZeros(unsigned) - 24;
      break;
    }
    return count;
  }
}
