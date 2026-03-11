package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.channel.config.PasswordRecoveryProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PasswordRecoveryTimingEqualizerTest {

  @Test
  void shouldSleepOnlyForRemainingForgotDelay() {
    PasswordRecoveryProperties properties = properties();
    AtomicLong sleptMillis = new AtomicLong();
    PasswordRecoveryTimingEqualizer equalizer = new PasswordRecoveryTimingEqualizer(
        properties,
        () -> Duration.ofMillis(125).toNanos(),
        max -> 15L,
        sleptMillis::set
    );

    equalizer.equalizeForgot(0L);

    assertThat(sleptMillis.get()).isEqualTo(290L);
  }

  @Test
  void shouldSkipSleepWhenElapsedTimeAlreadyExceedsTarget() {
    PasswordRecoveryProperties properties = properties();
    AtomicLong sleptMillis = new AtomicLong(-1L);
    PasswordRecoveryTimingEqualizer equalizer = new PasswordRecoveryTimingEqualizer(
        properties,
        () -> Duration.ofMillis(450).toNanos(),
        max -> 0L,
        sleptMillis::set
    );

    equalizer.equalizeForgot(0L);

    assertThat(sleptMillis.get()).isEqualTo(-1L);
  }

  private PasswordRecoveryProperties properties() {
    PasswordRecoveryProperties properties = new PasswordRecoveryProperties();
    properties.getTiming().getForgot().setFloorMs(400L);
    properties.getTiming().getForgot().setJitterMaxMs(50L);
    return properties;
  }
}
