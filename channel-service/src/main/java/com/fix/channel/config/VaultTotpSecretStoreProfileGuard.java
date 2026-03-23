package com.fix.channel.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
  @ConditionalOnProperty(name = "auth.totp.secret-store", havingValue = "vault")
  public class VaultTotpSecretStoreProfileGuard {

  private static final Set<String> DISALLOWED_NONLOCAL_HOSTS = Set.of(
      "localhost",
      "127.0.0.1",
      "0.0.0.0",
      "::1",
      "[::1]",
      "host.docker.internal",
      "vault",
      "vault-init"
  );
  private static final Profiles NONLOCAL_PROFILES = Profiles.of(
      "prod",
      "production",
      "staging",
      "stage",
      "preprod",
      "uat"
  );

  private final TotpProperties properties;
  private final Environment environment;

  public VaultTotpSecretStoreProfileGuard(TotpProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @PostConstruct
  public void validate() {
    if (!environment.acceptsProfiles(NONLOCAL_PROFILES)) {
      return;
    }

    TotpProperties.Vault vault = properties.getVault();

    if (!StringUtils.hasText(vault.getBaseUrl())) {
      throw new IllegalStateException("auth.totp.vault.base-url must be configured for non-local profiles");
    }

    URI uri;
    try {
      uri = URI.create(vault.getBaseUrl());
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("auth.totp.vault.base-url must be a valid URI for non-local profiles", ex);
    }

    String scheme = uri.getScheme();
    String host = uri.getHost();

    if (!"https".equalsIgnoreCase(scheme)) {
      throw new IllegalStateException("auth.totp.vault.base-url must use https for non-local profiles");
    }

    if (!StringUtils.hasText(host)) {
      throw new IllegalStateException("auth.totp.vault.base-url must include a valid non-local host");
    }

    if (DISALLOWED_NONLOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
      throw new IllegalStateException("auth.totp.vault.base-url must not target a local Vault host in non-local profiles");
    }

    if (!StringUtils.hasText(vault.getToken()) || "root-token".equals(vault.getToken())) {
      throw new IllegalStateException("auth.totp.vault.token must be explicitly configured for non-local profiles");
    }
  }
}
