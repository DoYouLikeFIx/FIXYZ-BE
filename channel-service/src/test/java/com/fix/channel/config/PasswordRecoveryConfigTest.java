package com.fix.channel.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PasswordRecoveryConfigTest {

  private final PasswordRecoveryConfig passwordRecoveryConfig = new PasswordRecoveryConfig();

  @Test
  void shouldExposeCanonicalCleanupDefaults() {
    PasswordRecoveryProperties properties = new PasswordRecoveryProperties();

    assertThat(properties.getCleanup().getCadence()).isEqualTo(Duration.ofMinutes(15));
    assertThat(properties.getCleanup().getRetention()).isEqualTo(Duration.ofDays(30));
    assertThat(properties.getCleanup().getBatchSize()).isEqualTo(500);
    assertThat(properties.getCleanup().getMaxBatchesPerRun()).isEqualTo(8);
    assertThat(properties.getCleanup().getMaxRunSeconds()).isEqualTo(20);
    assertThat(properties.getCleanup().getBacklogAlertThreshold()).isEqualTo(10_000L);
  }

  @Test
  void shouldExposeCleanupCadenceMillisFromProperties() {
    PasswordRecoveryProperties properties = new PasswordRecoveryProperties();
    properties.getCleanup().setCadence(Duration.ofMinutes(15));

    long cadenceMillis = passwordRecoveryConfig.passwordRecoveryCleanupCadenceMillis(properties);

    assertThat(cadenceMillis).isEqualTo(Duration.ofMinutes(15).toMillis());
  }
}
