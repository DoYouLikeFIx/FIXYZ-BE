package com.fix.fepgateway.dataplane.marketdata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class QuoteSnapshotIdGenerator {

  public String generate(NormalizedQuoteEvent event) {
    String canonical = String.join(
        "|",
        event.provider(),
        event.symbol(),
        event.sourceMode().name(),
        Long.toString(event.quoteAsOf().getEpochSecond()),
        Integer.toString(event.quoteAsOf().getNano()),
        Long.toString(event.streamOffset())
    );
    return "qsnap_" + sha256Hex(canonical);
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      byte[] hash = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hash.length * 2);
      for (byte current : hash) {
        builder.append(String.format("%02x", current));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
    }
  }
}
