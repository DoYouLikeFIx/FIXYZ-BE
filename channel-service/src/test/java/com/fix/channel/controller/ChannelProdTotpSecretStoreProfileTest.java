package com.fix.channel.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.channel.service.TotpSecretStore;
import com.fix.channel.service.VaultTotpSecretStore;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:channel_prod_totp;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "AUTH_TOTP_VAULT_BASE_URL=https://vault.example.internal",
    "AUTH_TOTP_VAULT_TOKEN=test-vault-token",
    "INTERNAL_SECRET=test-internal-secret",
    "COREBANK_INTERNAL_SECRET=test-internal-secret",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "AUTH_TOTP_VAULT_BASE_URL=http://127.0.0.1:8200",
    "AUTH_TOTP_VAULT_TOKEN=test-vault-token",
    "COREBANK_INTERNAL_SECRET=test-corebank-internal-secret",
    "spring.session.store-type=none",
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
class ChannelProdTotpSecretStoreProfileTest {

  @Autowired
  private TotpSecretStore totpSecretStore;

  @Test
  void shouldUseVaultBackedTotpSecretStoreInProdProfile() {
    Properties properties = loadProdProperties();

    assertThat(properties.getProperty("auth.totp.secret-store")).isEqualTo("vault");
    assertThat(properties.getProperty("auth.totp.vault.base-url")).isEqualTo("${AUTH_TOTP_VAULT_BASE_URL}");
    assertThat(properties.getProperty("auth.totp.vault.token")).isEqualTo("${AUTH_TOTP_VAULT_TOKEN}");
    assertThat(properties.getProperty("auth.totp.vault.trust-store-path")).isEqualTo("${AUTH_TOTP_VAULT_TRUST_STORE_PATH:}");
    assertThat(totpSecretStore).isInstanceOf(VaultTotpSecretStore.class);
  }

  private Properties loadProdProperties() {
    YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
    factoryBean.setResources(new ClassPathResource("application-prod.yml"));
    factoryBean.afterPropertiesSet();
    return factoryBean.getObject();
  }
}
