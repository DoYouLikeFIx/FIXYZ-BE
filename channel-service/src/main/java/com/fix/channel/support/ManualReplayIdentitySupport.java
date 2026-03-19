package com.fix.channel.support;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public final class ManualReplayIdentitySupport {

  private ManualReplayIdentitySupport() {
  }

  public static String operatorIdFor(String memberNo) {
    byte[] digest = sha256(normalize(memberNo));
    digest[6] = (byte) ((digest[6] & 0x0f) | 0x40);
    digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
    ByteBuffer buffer = ByteBuffer.wrap(digest, 0, 16);
    return new UUID(buffer.getLong(), buffer.getLong()).toString();
  }

  public static String replayFingerprint(
      String clOrdId,
      String manualDecision,
      String approvedBy,
      String evidenceRef,
      String reason,
      Long executionPrice,
      String operatorId
  ) {
    String payload = String.join(
        "|",
        normalize(clOrdId),
        normalize(manualDecision).toUpperCase(),
        normalize(approvedBy).toLowerCase(),
        normalize(evidenceRef),
        normalize(reason),
        executionPrice == null ? "" : executionPrice.toString(),
        normalize(operatorId).toLowerCase()
    );
    return HexFormat.of().formatHex(sha256(payload));
  }

  private static byte[] sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException("manual replay identity hash unavailable", ex);
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
