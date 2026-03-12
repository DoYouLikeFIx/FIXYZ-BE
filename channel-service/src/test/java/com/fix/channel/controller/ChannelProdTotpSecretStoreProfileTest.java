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
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
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
    assertThat(totpSecretStore).isInstanceOf(VaultTotpSecretStore.class);
  }

  private Properties loadProdProperties() {
    YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
    factoryBean.setResources(new ClassPathResource("application-prod.yml"));
    factoryBean.afterPropertiesSet();
    return factoryBean.getObject();
  }
}
