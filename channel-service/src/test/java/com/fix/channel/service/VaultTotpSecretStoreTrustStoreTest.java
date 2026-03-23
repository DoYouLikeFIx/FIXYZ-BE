package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.config.TotpProperties;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class VaultTotpSecretStoreTrustStoreTest {

  @Test
  void shouldAllowCustomTrustStoreConfigurationForVaultClient() throws Exception {
    Path trustStorePath = Files.createTempFile("vault-totp-trust-store", ".p12");
    char[] password = "changeit".toCharArray();
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, password);
    try (OutputStream outputStream = Files.newOutputStream(trustStorePath)) {
      keyStore.store(outputStream, password);
    }

    TotpProperties properties = new TotpProperties();
    properties.setSecretStore("vault");
    properties.getVault().setBaseUrl("https://vault.example.internal");
    properties.getVault().setToken("runtime-token");
    properties.getVault().setTrustStorePath(trustStorePath.toString());
    properties.getVault().setTrustStorePassword("changeit");
    properties.getVault().setTrustStoreType("PKCS12");

    assertThatCode(() -> new VaultTotpSecretStore(
        RestClient.builder(),
        new ObjectMapper(),
        Clock.systemUTC(),
        properties
    )).doesNotThrowAnyException();
  }
}
