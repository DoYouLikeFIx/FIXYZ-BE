package com.fix.channel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.config.PasswordRecoveryProperties;
import com.fix.channel.vo.PasswordForgotChallengeResult;
import com.fix.channel.vo.PasswordForgotChallengeResult.ChallengePayload;
import com.fix.channel.vo.PasswordForgotChallengeResult.ProofOfWorkPayload;
import com.fix.channel.vo.PasswordForgotChallengeResult.SuccessCondition;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class ProofOfWorkPasswordRecoveryChallengeService implements PasswordRecoveryChallengeProvider {

  private static final String TOKEN_PREFIX = "v2.";
  private static final String CHALLENGE_SCOPE_PREFIX = "ch:password-recovery:v2:scope:";
  private static final String CHALLENGE_CONSUMED_PREFIX = "ch:password-recovery:v2:consumed:";
  private static final String CHALLENGE_KIND = "proof-of-work";
  private static final String ALGORITHM = "SHA-256";
  private static final String ANSWER_FORMAT = "nonce-decimal";
  private static final String INPUT_TEMPLATE = "{seed}:{nonce}";
  private static final String INPUT_ENCODING = "utf-8";
  private static final String SUCCESS_CONDITION_TYPE = "leading-zero-bits";
  private static final DefaultRedisScript<Long> CONSUME_SCRIPT = createConsumeScript();

  private final PasswordRecoveryProperties properties;
  private final PasswordRecoveryTokenService tokenService;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  public ProofOfWorkPasswordRecoveryChallengeService(
      PasswordRecoveryProperties properties,
      PasswordRecoveryTokenService tokenService,
      ObjectMapper objectMapper,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider
  ) {
    this.properties = properties;
    this.tokenService = tokenService;
    this.objectMapper = objectMapper;
    this.redisTemplateProvider = redisTemplateProvider;
  }

  @Override
  public boolean isProofOfWorkProvider() {
    return true;
  }

  @Override
  public boolean supportsToken(String challengeToken) {
    return challengeToken != null && challengeToken.startsWith(TOKEN_PREFIX);
  }

  @Override
  public PasswordForgotChallengeResult issue(String rawEmail, String normalizedEmail, jakarta.servlet.http.HttpServletRequest request) {
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plusSeconds(properties.getChallenge().getTtlSeconds());
    String sessionId = request.getSession(true).getId();
    String challengeId = UUID.randomUUID().toString();
    String seed = tokenService.generateRawResetToken();
    int difficultyBits = Math.max(1, properties.getChallenge().getDifficultyBits());

    ChallengeEnvelope envelope = new ChallengeEnvelope(
        2,
        challengeId,
        CHALLENGE_KIND,
        issuedAt.toEpochMilli(),
        expiresAt.toEpochMilli(),
        new ChallengePayload(
            CHALLENGE_KIND,
            new ProofOfWorkPayload(
                ALGORITHM,
                seed,
                difficultyBits,
                ANSWER_FORMAT,
                INPUT_TEMPLATE,
                INPUT_ENCODING,
                new SuccessCondition(SUCCESS_CONDITION_TYPE, difficultyBits)
            )
        ),
        tokenService.fingerprint(normalizedEmail),
        tokenService.fingerprint(rawEmail == null ? "" : rawEmail),
        sessionId
    );

    try {
      StringRedisTemplate redisTemplate = requireRedis();
      redisTemplate.opsForValue().set(
          scopeKey(sessionId, envelope.normalizedEmailHash()),
          challengeId,
          Duration.ofSeconds(properties.getChallenge().getTtlSeconds())
      );
      return PasswordForgotChallengeResult.proofOfWork(
          challengeId,
          encodeToken(envelope),
          CHALLENGE_KIND,
          properties.getChallenge().getTtlSeconds(),
          envelope.challengeIssuedAtEpochMs(),
          envelope.challengeExpiresAtEpochMs(),
          envelope.challengePayload()
      );
    } catch (BusinessException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_BOOTSTRAP_UNAVAILABLE,
          "recovery challenge bootstrap is currently unavailable"
      );
    }
  }

  @Override
  public void validate(
      String rawEmail,
      String normalizedEmail,
      String challengeToken,
      String challengeAnswer,
      jakarta.servlet.http.HttpServletRequest request
  ) {
    if (challengeToken == null || challengeToken.isBlank() || challengeAnswer == null || challengeAnswer.isBlank()) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
          "recovery challenge token is invalid or expired"
      );
    }

    ChallengeEnvelope envelope = decodeToken(challengeToken);

    if (envelope.challengeContractVersion() != 2
        || !CHALLENGE_KIND.equals(envelope.challengeType())
        || envelope.challengePayload() == null
        || !CHALLENGE_KIND.equals(envelope.challengePayload().kind())
        || envelope.challengePayload().proofOfWork() == null) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
          "recovery challenge token is invalid or expired"
      );
    }

    Instant expiresAt = Instant.ofEpochMilli(envelope.challengeExpiresAtEpochMs());
    if (!expiresAt.isAfter(Instant.now())) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
          "recovery challenge token is invalid or expired"
      );
    }

    String normalizedEmailHash = tokenService.fingerprint(normalizedEmail);
    String submittedEmailDigest = tokenService.fingerprint(rawEmail == null ? "" : rawEmail);
    String sessionId = request.getSession(true).getId();
    if (!normalizedEmailHash.equals(envelope.normalizedEmailHash())
        || !submittedEmailDigest.equals(envelope.submittedEmailDigest())
        || !sessionId.equals(envelope.sessionId())) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
          "recovery challenge token is invalid or expired"
      );
    }

    try {
      StringRedisTemplate redisTemplate = requireRedis();
      String latestChallengeId = redisTemplate.opsForValue().get(scopeKey(sessionId, normalizedEmailHash));
      if (!envelope.challengeId().equals(latestChallengeId)) {
        throw new BusinessException(
            ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_REPLAYED,
            "recovery challenge token already used"
        );
      }

      if (!isValidProof(envelope.challengePayload().proofOfWork(), challengeAnswer)) {
        throw new BusinessException(
            ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
            "recovery challenge token is invalid or expired"
        );
      }

      Long consumed = redisTemplate.execute(
          CONSUME_SCRIPT,
          java.util.List.of(scopeKey(sessionId, normalizedEmailHash), consumedKey(envelope.challengeId())),
          envelope.challengeId(),
          String.valueOf(Duration.ofSeconds(properties.getChallenge().getTtlSeconds()).toMillis())
      );
      if (consumed == null || consumed < 1L) {
        throw new BusinessException(
            ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_REPLAYED,
            "recovery challenge token already used"
        );
      }
    } catch (BusinessException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_VERIFY_UNAVAILABLE,
          "recovery challenge verification is currently unavailable"
      );
    }
  }

  @Override
  public ChallengeEventContext describeVerifyContext(
      String challengeToken,
      ChallengeEventContext fallbackContext
  ) {
    return supportsToken(challengeToken)
        ? new ChallengeEventContext(challengeContractVersionLabel(), true, true)
        : fallbackContext;
  }

  private boolean isValidProof(ProofOfWorkPayload proofOfWork, String answer) {
    if (proofOfWork == null || answer == null || answer.isBlank() || !answer.chars().allMatch(Character::isDigit)) {
      return false;
    }

    BigInteger nonce;
    try {
      nonce = new BigInteger(answer, 10);
      if (nonce.signum() < 0) {
        return false;
      }
    } catch (NumberFormatException ex) {
      return false;
    }

    try {
      MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
      byte[] hash = digest.digest((proofOfWork.seed() + ":" + nonce.toString(10)).getBytes(StandardCharsets.UTF_8));
      return leadingZeroBits(hash) >= proofOfWork.difficultyBits();
    } catch (Exception ex) {
      return false;
    }
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

  private ChallengeEnvelope decodeToken(String challengeToken) {
    try {
      if (!challengeToken.startsWith(TOKEN_PREFIX)) {
        throw new IllegalArgumentException("not a v2 challenge token");
      }
      String[] parts = challengeToken.substring(TOKEN_PREFIX.length()).split("\\.", 2);
      if (parts.length != 2 || !tokenService.signaturesMatch(parts[0], parts[1])) {
        throw new IllegalArgumentException("challenge signature mismatch");
      }
      byte[] json = Base64.getUrlDecoder().decode(parts[0]);
      return objectMapper.readValue(json, ChallengeEnvelope.class);
    } catch (Exception ex) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_INVALID,
          "recovery challenge token is invalid or expired"
      );
    }
  }

  private String encodeToken(ChallengeEnvelope envelope) {
    try {
      String payload = Base64.getUrlEncoder().withoutPadding()
          .encodeToString(objectMapper.writeValueAsBytes(envelope));
      return TOKEN_PREFIX + payload + "." + tokenService.sign(payload);
    } catch (Exception ex) {
      throw new BusinessException(
          ErrorCode.AUTH_PASSWORD_RECOVERY_CHALLENGE_BOOTSTRAP_UNAVAILABLE,
          "recovery challenge bootstrap is currently unavailable"
      );
    }
  }

  private String scopeKey(String sessionId, String normalizedEmailHash) {
    return CHALLENGE_SCOPE_PREFIX + sessionId + ":" + normalizedEmailHash;
  }

  private String consumedKey(String challengeId) {
    return CHALLENGE_CONSUMED_PREFIX + challengeId;
  }

  private StringRedisTemplate requireRedis() {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      throw new IllegalStateException("redis is required for password recovery challenges");
    }
    return redisTemplate;
  }

  private static DefaultRedisScript<Long> createConsumeScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText("""
        local latest = redis.call('GET', KEYS[1])
        if latest ~= ARGV[1] then
          return 0
        end
        local consumed = redis.call('SETNX', KEYS[2], '1')
        if consumed == 0 then
          return 0
        end
        redis.call('PEXPIRE', KEYS[2], ARGV[2])
        return 1
        """);
    script.setResultType(Long.class);
    return script;
  }

  public record ChallengeEnvelope(
      int challengeContractVersion,
      String challengeId,
      String challengeType,
      long challengeIssuedAtEpochMs,
      long challengeExpiresAtEpochMs,
      ChallengePayload challengePayload,
      String normalizedEmailHash,
      String submittedEmailDigest,
      String sessionId
  ) {
  }
}
