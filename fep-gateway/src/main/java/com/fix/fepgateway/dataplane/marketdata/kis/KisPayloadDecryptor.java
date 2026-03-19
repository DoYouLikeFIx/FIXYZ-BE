package com.fix.fepgateway.dataplane.marketdata.kis;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class KisPayloadDecryptor {

  private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

  public String decrypt(String encryptedPayload, KisDecryptionContext decryptionContext) {
    if (encryptedPayload == null || encryptedPayload.isBlank()) {
      throw new KisFrameParseException(
          KisFrameFailureType.DECODE_ERROR,
          "Encrypted KIS payload must not be blank"
      );
    }

    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(decryptionContext.key().getBytes(StandardCharsets.UTF_8), "AES"),
          new IvParameterSpec(decryptionContext.iv().getBytes(StandardCharsets.UTF_8))
      );
      byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(encryptedPayload));
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new KisFrameParseException(
          KisFrameFailureType.DECODE_ERROR,
          "Failed to decrypt KIS websocket payload",
          exception
      );
    }
  }
}
