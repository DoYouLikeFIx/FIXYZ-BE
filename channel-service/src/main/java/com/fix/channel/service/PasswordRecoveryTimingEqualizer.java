package com.fix.channel.service;

import com.fix.channel.config.PasswordRecoveryProperties;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PasswordRecoveryTimingEqualizer {

  private final PasswordRecoveryProperties properties;
  private final LongSupplier nanoTimeSupplier;
  private final LongUnaryOperator jitterSupplier;
  private final LongConsumer sleepConsumer;

  @Autowired
  public PasswordRecoveryTimingEqualizer(PasswordRecoveryProperties properties) {
    this(
        properties,
        System::nanoTime,
        max -> max <= 0 ? 0 : ThreadLocalRandom.current().nextLong(max + 1),
        PasswordRecoveryTimingEqualizer::sleepQuietly
    );
  }

  PasswordRecoveryTimingEqualizer(
      PasswordRecoveryProperties properties,
      LongSupplier nanoTimeSupplier,
      LongUnaryOperator jitterSupplier,
      LongConsumer sleepConsumer
  ) {
    this.properties = properties;
    this.nanoTimeSupplier = nanoTimeSupplier;
    this.jitterSupplier = jitterSupplier;
    this.sleepConsumer = sleepConsumer;
  }

  public long start() {
    return nanoTimeSupplier.getAsLong();
  }

  public void equalizeForgot(long startNanos) {
    equalize(startNanos, properties.getTiming().getForgot());
  }

  public void equalizeReset(long startNanos) {
    equalize(startNanos, properties.getTiming().getReset());
  }

  void equalize(long startNanos, PasswordRecoveryProperties.Delay delay) {
    long elapsedMillis = Duration.ofNanos(Math.max(0L, nanoTimeSupplier.getAsLong() - startNanos)).toMillis();
    long targetMillis = delay.getFloorMs() + Math.max(0L, jitterSupplier.applyAsLong(delay.getJitterMaxMs()));
    long remainingMillis = targetMillis - elapsedMillis;
    if (remainingMillis > 0L) {
      sleepConsumer.accept(remainingMillis);
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
