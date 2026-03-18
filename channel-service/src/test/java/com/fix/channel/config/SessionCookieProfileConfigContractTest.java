package com.fix.channel.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SessionCookieProfileConfigContractTest {

  @Test
  void shouldUseSecureCookieByDefaultInBaseProfile() {
    String content = loadResource("application.yml");
    assertThat(content).contains("secure: ${SESSION_COOKIE_SECURE:true}");
  }

  @Test
  void shouldDisableSecureCookieInLocalProfileOverride() {
    String content = loadResource("application-local.yml");
    assertThat(content).contains("secure: false");
  }

  @Test
  void shouldUseSameSiteNoneInProdProfile() {
    String content = loadResource("application-prod.yml");
    assertThat(content).contains("same-site: none");
  }

  @Test
  void shouldKeepSecureCookieEnabledInProdProfile() {
    String content = loadResource("application-prod.yml");
    assertThat(content).contains("secure: true");
  }

  private String loadResource(String resourceName) {
    try {
      Path moduleRelative = Path.of("src", "main", "resources", resourceName);
      if (Files.exists(moduleRelative)) {
        return Files.readString(moduleRelative, StandardCharsets.UTF_8);
      }
      Path beRootRelative = Path.of("channel-service", "src", "main", "resources", resourceName);
      assertThat(Files.exists(beRootRelative)).isTrue();
      return Files.readString(beRootRelative, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("failed to load resource: " + resourceName, exception);
    }
  }
}
