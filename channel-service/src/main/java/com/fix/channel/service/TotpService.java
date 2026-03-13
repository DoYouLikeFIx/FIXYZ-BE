package com.fix.channel.service;

import com.fix.channel.entity.Member;
import com.fix.channel.config.TotpProperties;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TotpService {

  private static final int SECRET_BYTES = 20;
  private static final int OTP_DIGITS = 6;
  private static final long PERIOD_SECONDS = 30L;

  private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final Clock clock;
  private final TotpSecretStore totpSecretStore;
  private final TotpProperties properties;

  public TotpEnrollment bootstrap(Member member, Instant expiresAt, String loginToken) {
    String manualEntryKey = totpSecretStore.getOrCreatePendingSecret(member, loginToken, expiresAt).manualEntryKey();
    String qrUri = qrUri(member, manualEntryKey);
    String enrollmentToken = enrollmentToken(loginToken);
    return new TotpEnrollment(manualEntryKey, qrUri, enrollmentToken, expiresAt);
  }

  public boolean isValidEnrollmentToken(String loginToken, String enrollmentToken) {
    if (loginToken == null || loginToken.isBlank() || enrollmentToken == null || enrollmentToken.isBlank()) {
      return false;
    }
    return constantTimeEquals(enrollmentToken(loginToken.trim()), enrollmentToken.trim());
  }

  public TotpVerification verifyCurrentCode(Member member, String otpCode) {
    String manualEntryKey = totpSecretStore.findActiveSecret(member).orElse(null);
    if (manualEntryKey == null || manualEntryKey.isBlank()) {
      return TotpVerification.unmatched();
    }
    return verifyAgainstSecret(manualEntryKey, otpCode);
  }

  public TotpVerification verifyPendingCode(Member member, String loginToken, String otpCode) {
    TotpSecretStore.PendingTotpSecret pendingSecret = totpSecretStore.findPendingSecret(member, loginToken)
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_LOGIN_TOKEN_EXPIRED, "login token expired or invalid"));
    return verifyAgainstSecret(pendingSecret.manualEntryKey(), otpCode);
  }

  public void promotePendingSecret(Member member, String loginToken) {
    totpSecretStore.promotePendingSecret(member, loginToken);
  }

  public void discardPendingSecret(Member member, String loginToken) {
    totpSecretStore.discardPendingSecret(member, loginToken);
  }

  public void provisionActiveSecret(Member member) {
    totpSecretStore.saveActiveSecret(member, generateRandomManualEntryKey());
  }

  public boolean hasActiveSecret(Member member) {
    return totpSecretStore.findActiveSecret(member).isPresent();
  }

  public void terminalizeActiveSecret(Member member) {
    totpSecretStore.terminalizeActiveSecret(member);
  }

  public String currentCode(Member member) {
    String manualEntryKey = totpSecretStore.findActiveSecret(member)
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOTP_ENROLLMENT_REQUIRED, "totp enrollment required"));
    return currentCodeForManualEntryKey(manualEntryKey);
  }

  public String currentCodeForManualEntryKey(String manualEntryKey) {
    return generateCode(base32Decode(manualEntryKey), currentStep());
  }

  public TotpVerification verifyAgainstSecret(String manualEntryKey, String otpCode) {
    String normalizedOtp = normalizeOtp(otpCode);
    byte[] secret = base32Decode(manualEntryKey);
    long currentStep = currentStep();
    for (long offset = -1; offset <= 1; offset++) {
      long windowIndex = currentStep + offset;
      if (generateCode(secret, windowIndex).equals(normalizedOtp)) {
        return TotpVerification.matched(windowIndex, normalizedOtp);
      }
    }
    return TotpVerification.unmatched();
  }

  private String qrUri(Member member, String manualEntryKey) {
    String label = urlEncode(properties.getIssuer() + ":" + member.getEmail());
    String issuerQuery = urlEncode(properties.getIssuer());
    return "otpauth://totp/" + label
        + "?secret=" + manualEntryKey
        + "&issuer=" + issuerQuery
        + "&algorithm=SHA1&digits=" + OTP_DIGITS
        + "&period=" + PERIOD_SECONDS;
  }

  private String enrollmentToken(String loginToken) {
    byte[] digest = hmac("HmacSHA256", properties.getEnrollmentPepper(), "enroll:" + loginToken);
    return base32Encode(Arrays.copyOf(digest, 20));
  }

  private String generateCode(byte[] secret, long counter) {
    byte[] counterBytes = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
    byte[] hash = hmac("HmacSHA1", secret, counterBytes);
    int offset = hash[hash.length - 1] & 0x0f;
    int binary = ((hash[offset] & 0x7f) << 24)
        | ((hash[offset + 1] & 0xff) << 16)
        | ((hash[offset + 2] & 0xff) << 8)
        | (hash[offset + 3] & 0xff);
    int otp = binary % 1_000_000;
    return String.format("%06d", otp);
  }

  private long currentStep() {
    return Instant.now(clock).getEpochSecond() / PERIOD_SECONDS;
  }

  private String normalizeOtp(String otpCode) {
    if (otpCode == null) {
      return "";
    }
    return otpCode.trim();
  }

  private byte[] hmac(String algorithm, String key, String value) {
    return hmac(algorithm, key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
  }

  private byte[] hmac(String algorithm, byte[] key, byte[] value) {
    try {
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(key, algorithm));
      return mac.doFinal(value);
    } catch (GeneralSecurityException ex) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "totp crypto operation failed", ex);
    }
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private boolean constantTimeEquals(String left, String right) {
    byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
    byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
    return java.security.MessageDigest.isEqual(leftBytes, rightBytes);
  }

  static String generateRandomManualEntryKey() {
    byte[] bytes = new byte[SECRET_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return base32Encode(bytes);
  }

  private static String base32Encode(byte[] bytes) {
    StringBuilder encoded = new StringBuilder((bytes.length * 8 + 4) / 5);
    int buffer = 0;
    int bitsLeft = 0;
    for (byte value : bytes) {
      buffer = (buffer << 8) | (value & 0xff);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        encoded.append(BASE32_ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1f]);
        bitsLeft -= 5;
      }
    }
    if (bitsLeft > 0) {
      encoded.append(BASE32_ALPHABET[(buffer << (5 - bitsLeft)) & 0x1f]);
    }
    return encoded.toString();
  }

  private byte[] base32Decode(String manualEntryKey) {
    String normalized = manualEntryKey == null ? "" : manualEntryKey.trim().replace("=", "").toUpperCase(Locale.ROOT);
    if (normalized.isBlank()) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "totp secret missing");
    }

    int buffer = 0;
    int bitsLeft = 0;
    byte[] decoded = new byte[(normalized.length() * 5) / 8];
    int index = 0;
    for (int i = 0; i < normalized.length(); i++) {
      int value = base32Index(normalized.charAt(i));
      buffer = (buffer << 5) | value;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        decoded[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
        bitsLeft -= 8;
      }
    }
    return index == decoded.length ? decoded : Arrays.copyOf(decoded, index);
  }

  private int base32Index(char character) {
    for (int i = 0; i < BASE32_ALPHABET.length; i++) {
      if (BASE32_ALPHABET[i] == character) {
        return i;
      }
    }
    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "invalid base32 totp secret");
  }

  public record TotpEnrollment(
      String manualEntryKey,
      String qrUri,
      String enrollmentToken,
      Instant expiresAt
  ) {
  }

  public record TotpVerification(boolean matched, long windowIndex, String normalizedOtp) {

    static TotpVerification matched(long windowIndex, String normalizedOtp) {
      return new TotpVerification(true, windowIndex, normalizedOtp);
    }

    static TotpVerification unmatched() {
      return new TotpVerification(false, Long.MIN_VALUE, "");
    }
  }
}
