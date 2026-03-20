package com.fix.fepgateway.dataplane.marketdata.replay;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import com.fix.fepgateway.dataplane.marketdata.ReplayCursorSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ReplayQuoteEventGenerator {

  private static final Instant REPLAY_ANCHOR = Instant.parse("2026-01-01T00:00:00Z");

  public NormalizedQuoteEvent generate(ReplayCursorSpec replayCursorSpec) {
    long cursorOffset = replayCursorSpec.cursorOffset();
    long basePrice = 10_000L + modulo(hash(replayCursorSpec, "base-price"), 200_000L);
    long drift = modulo(hash(replayCursorSpec, "drift", cursorOffset), 4_001L) - 2_000L;
    long spread = 1L + modulo(hash(replayCursorSpec, "spread", cursorOffset), 30L);
    long lastTrade = Math.max(100L, basePrice + drift);
    long bestBid = Math.max(1L, lastTrade - spread);
    long bestAsk = lastTrade + spread;
    long baseSeconds = modulo(hash(replayCursorSpec, "base-seconds"), 86_400L);

    return new NormalizedQuoteEvent(
        "REPLAY",
        replayCursorSpec.symbol(),
        FepQuoteSourceMode.REPLAY,
        REPLAY_ANCHOR.plusSeconds(baseSeconds + cursorOffset),
        bestBid,
        bestAsk,
        lastTrade,
        cursorOffset,
        false
    );
  }

  private long hash(ReplayCursorSpec replayCursorSpec, String discriminator) {
    return hash(replayCursorSpec, discriminator, -1L);
  }

  private long hash(ReplayCursorSpec replayCursorSpec, String discriminator, long cursorOffset) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      messageDigest.update(replayCursorSpec.seed().getBytes(StandardCharsets.UTF_8));
      messageDigest.update((byte) '|');
      messageDigest.update(replayCursorSpec.symbol().getBytes(StandardCharsets.UTF_8));
      messageDigest.update((byte) '|');
      messageDigest.update(discriminator.getBytes(StandardCharsets.UTF_8));
      if (cursorOffset >= 0) {
        messageDigest.update((byte) '|');
        messageDigest.update(Long.toString(cursorOffset).getBytes(StandardCharsets.UTF_8));
      }
      byte[] digest = messageDigest.digest();
      long value = 0L;
      for (int i = 0; i < Long.BYTES; i++) {
        value = (value << 8) | (digest[i] & 0xffL);
      }
      return value;
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private long modulo(long value, long divisor) {
    return Math.floorMod(value, divisor);
  }
}
