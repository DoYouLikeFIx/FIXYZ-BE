package com.fix.fepgateway.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(MockitoExtension.class)
class FepGatewayProdDocsDisabledTest {

  @Test
  void shouldDisableApiDocsInProdProfile() {
    Properties properties = loadProdProperties();

    assertThat(properties.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
    assertThat(properties.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
  }

  private Properties loadProdProperties() {
    YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
    factoryBean.setResources(new ClassPathResource("application-prod.yml"));
    factoryBean.afterPropertiesSet();
    return factoryBean.getObject();
  }
}
