package com.fix.channel.service;

import com.fix.channel.config.PasswordRecoveryProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PasswordRecoveryTokenService {

  private static final String HMAC_SHA_256 = "HmacSHA256";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final PasswordRecoveryProperties properties;

  public PasswordRecoveryTokenService(PasswordRecoveryProperties properties) {
    this.properties = properties;
  }

  public String generateRawResetToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public TokenHash candidateCurrentHash(String rawToken) {
    return new TokenHash(
        (short) properties.getToken().getCurrentPepperVersion(),
        hmacHex(properties.getToken().getCurrentPepper(), rawToken)
    );
  }

  public List<TokenHash> candidateHashes(String rawToken) {
    List<TokenHash> hashes = new ArrayList<>();
    hashes.add(candidateCurrentHash(rawToken));

    String previousPepper = properties.getToken().getPreviousPepper();
    if (previousPepper != null && !previousPepper.isBlank()
        && properties.getToken().getPreviousPepperVersion() != properties.getToken().getCurrentPepperVersion()) {
      hashes.add(new TokenHash(
          (short) properties.getToken().getPreviousPepperVersion(),
          hmacHex(previousPepper, rawToken)
      ));
    }
    return hashes;
  }

  public String fingerprint(String value) {
    return sha256Hex(value == null ? "" : value);
  }

  public String sign(String payload) {
    return hmacHex(properties.getToken().getChallengeSigningSecret(), payload);
  }

  public boolean signaturesMatch(String payload, String signature) {
    return MessageDigest.isEqual(
        sign(payload).getBytes(StandardCharsets.UTF_8),
        (signature == null ? "" : signature).getBytes(StandardCharsets.UTF_8)
    );
  }

  private String hmacHex(String secret, String value) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA_256);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("unable to calculate token hash", ex);
    }
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("unable to calculate fingerprint", ex);
    }
  }

  public record TokenHash(short pepperVersion, String hash) {
  }
}
