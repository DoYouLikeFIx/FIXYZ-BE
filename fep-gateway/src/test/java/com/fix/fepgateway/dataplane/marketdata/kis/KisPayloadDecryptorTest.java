package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class KisPayloadDecryptorTest {

  private static final KisDecryptionContext DECRYPTION_CONTEXT = new KisDecryptionContext(
      "12345678901234567890123456789012",
      "1234567890123456"
  );

  private final KisPayloadDecryptor decryptor = new KisPayloadDecryptor();

  @Test
  void shouldDecryptBase64EncodedAesPayload() throws Exception {
    String payload = "005930^093001^70100";
    String encryptedPayload = encrypt(payload, DECRYPTION_CONTEXT);

    String decryptedPayload = decryptor.decrypt(encryptedPayload, DECRYPTION_CONTEXT);

    assertThat(decryptedPayload).isEqualTo(payload);
  }

  @Test
  void shouldClassifyInvalidCiphertextAsDecodeError() {
    assertThatThrownBy(() -> decryptor.decrypt("not-base64!", DECRYPTION_CONTEXT))
        .isInstanceOfSatisfying(KisFrameParseException.class, exception ->
            assertThat(exception.getFailureType()).isEqualTo(KisFrameFailureType.DECODE_ERROR));
  }

  private String encrypt(String payload, KisDecryptionContext decryptionContext) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(
        Cipher.ENCRYPT_MODE,
        new SecretKeySpec(decryptionContext.key().getBytes(StandardCharsets.UTF_8), "AES"),
        new IvParameterSpec(decryptionContext.iv().getBytes(StandardCharsets.UTF_8))
    );
    return Base64.getEncoder().encodeToString(cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }
}
