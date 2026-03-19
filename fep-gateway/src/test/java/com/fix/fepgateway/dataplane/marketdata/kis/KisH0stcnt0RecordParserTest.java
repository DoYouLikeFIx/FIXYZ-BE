package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class KisH0stcnt0RecordParserTest {

  private static final KisDecryptionContext DECRYPTION_CONTEXT = new KisDecryptionContext(
      "12345678901234567890123456789012",
      "1234567890123456"
  );

  private final KisH0stcnt0RecordParser parser = new KisH0stcnt0RecordParser(
      new KisRealtimeFrameParser(),
      new KisPayloadDecryptor()
  );

  @Test
  void shouldParsePlainH0stcnt0Record() {
    String payload = recordPayload("005930", "093001", "70100", "70200", "70000", "20260319");

    KisH0stcnt0Record record = parser.parse("0|H0STCNT0|001|" + payload).getFirst();

    assertThat(record.symbol()).isEqualTo("005930");
    assertThat(record.tradeHour()).isEqualTo("093001");
    assertThat(record.lastTrade()).isEqualTo("70100");
    assertThat(record.bestAsk()).isEqualTo("70200");
    assertThat(record.bestBid()).isEqualTo("70000");
    assertThat(record.businessDate()).isEqualTo("20260319");
  }

  @Test
  void shouldSplitMultipleRecordsUsingCountField() {
    String firstRecord = recordPayload("005930", "093001", "70100", "70200", "70000", "20260319");
    String secondRecord = recordPayload("000660", "093002", "201500", "201700", "201300", "20260319");

    var records = parser.parse("0|H0STCNT0|002|" + firstRecord + "^" + secondRecord);

    assertThat(records).hasSize(2);
    assertThat(records.get(0).symbol()).isEqualTo("005930");
    assertThat(records.get(1).symbol()).isEqualTo("000660");
    assertThat(records.get(1).lastTrade()).isEqualTo("201500");
  }

  @Test
  void shouldDecryptEncryptedPayloadBeforeRecordSplit() throws Exception {
    String payload = recordPayload("005930", "093001", "70100", "70200", "70000", "20260319");
    String encryptedPayload = encrypt(payload, DECRYPTION_CONTEXT);

    KisH0stcnt0Record record = parser.parse(
        "1|H0STCNT0|001|" + encryptedPayload,
        DECRYPTION_CONTEXT
    ).getFirst();

    assertThat(record.symbol()).isEqualTo("005930");
    assertThat(record.bestAsk()).isEqualTo("70200");
  }

  @Test
  void shouldRejectPayloadWhenFieldCountDoesNotMatchRecordCount() {
    String payload = recordPayload("005930", "093001", "70100", "70200", "70000", "20260319");

    assertThatThrownBy(() -> parser.parse("0|H0STCNT0|002|" + payload))
        .isInstanceOfSatisfying(KisFrameParseException.class, exception ->
            assertThat(exception.getFailureType()).isEqualTo(KisFrameFailureType.MALFORMED_PAYLOAD));
  }

  @Test
  void shouldRejectEncryptedFrameWithoutDecryptionContext() {
    assertThatThrownBy(() -> parser.parse("1|H0STCNT0|001|ciphertext"))
        .isInstanceOfSatisfying(KisFrameParseException.class, exception ->
            assertThat(exception.getFailureType()).isEqualTo(KisFrameFailureType.DECODE_ERROR));
  }

  @Test
  void shouldRejectUnsupportedTrId() {
    String payload = recordPayload("005930", "093001", "70100", "70200", "70000", "20260319");

    assertThatThrownBy(() -> parser.parse("0|H0STASP0|001|" + payload))
        .isInstanceOfSatisfying(KisFrameParseException.class, exception ->
            assertThat(exception.getFailureType()).isEqualTo(KisFrameFailureType.UNSUPPORTED_TR_ID));
  }

  private String recordPayload(
      String symbol,
      String tradeHour,
      String lastTrade,
      String bestAsk,
      String bestBid,
      String businessDate
  ) {
    String[] fields = new String[KisH0stcnt0Record.RECORD_FIELD_COUNT];
    Arrays.fill(fields, "");
    fields[0] = symbol;
    fields[1] = tradeHour;
    fields[2] = lastTrade;
    fields[10] = bestAsk;
    fields[11] = bestBid;
    fields[33] = businessDate;
    fields[34] = "2";
    fields[35] = "N";
    fields[45] = "70500";
    return String.join("^", fields);
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
