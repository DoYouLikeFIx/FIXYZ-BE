package com.fix.channel.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class VaultTotpSecretStoreProfileGuardTest {

  @Test
  void shouldRejectPlaintextVaultUrlForStagingProfile() {
    TotpProperties properties = vaultProperties("http://vault:8200", "runtime-token");
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("staging");

    VaultTotpSecretStoreProfileGuard guard = new VaultTotpSecretStoreProfileGuard(properties, environment);

    assertThatThrownBy(guard::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("https");
  }

  @Test
  void shouldRejectRootTokenForProdProfile() {
    TotpProperties properties = vaultProperties("https://vault.example.internal", "root-token");
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");

    VaultTotpSecretStoreProfileGuard guard = new VaultTotpSecretStoreProfileGuard(properties, environment);

    assertThatThrownBy(guard::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("auth.totp.vault.token");
  }

  @Test
  void shouldRejectMalformedVaultUrlForProductionAliasProfile() {
    TotpProperties properties = vaultProperties("https:///totp", "runtime-token");
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("production");

    VaultTotpSecretStoreProfileGuard guard = new VaultTotpSecretStoreProfileGuard(properties, environment);

    assertThatThrownBy(guard::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("valid non-local host");
  }

  @Test
  void shouldRejectSyntacticallyInvalidVaultUrlForProductionAliasProfile() {
    TotpProperties properties = vaultProperties("https://[broken", "runtime-token");
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("production");

    VaultTotpSecretStoreProfileGuard guard = new VaultTotpSecretStoreProfileGuard(properties, environment);

    assertThatThrownBy(guard::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("valid URI");
  }

  @Test
  void shouldRejectDockerHostAliasesForPreprodProfile() {
    TotpProperties properties = vaultProperties("https://host.docker.internal:8200", "runtime-token");
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("preprod");

    VaultTotpSecretStoreProfileGuard guard = new VaultTotpSecretStoreProfileGuard(properties, environment);

    assertThatThrownBy(guard::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not target a local Vault host");
  }

  @Test
  void shouldRejectIpv6LoopbackHostForUatProfile() {
    TotpProperties properties = vaultProperties("https://[::1]:8200", "runtime-token");
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("uat");

    VaultTotpSecretStoreProfileGuard guard = new VaultTotpSecretStoreProfileGuard(properties, environment);

    assertThatThrownBy(guard::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not target a local Vault host");
  }

  @Test
  void shouldAllowLocalVaultSettingsOutsideNonLocalProfiles() {
    TotpProperties properties = vaultProperties("http://vault:8200", "root-token");
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("local");

    VaultTotpSecretStoreProfileGuard guard = new VaultTotpSecretStoreProfileGuard(properties, environment);

    assertThatCode(guard::validate).doesNotThrowAnyException();
  }

  private TotpProperties vaultProperties(String baseUrl, String token) {
    TotpProperties properties = new TotpProperties();
    properties.setSecretStore("vault");
    properties.getVault().setBaseUrl(baseUrl);
    properties.getVault().setToken(token);
    return properties;
  }
}
