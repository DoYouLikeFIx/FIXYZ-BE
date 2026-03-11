package com.fix.channel;

import com.fix.channel.config.PasswordRecoveryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PasswordRecoveryProperties.class)
public class ChannelServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ChannelServiceApplication.class, args);
  }
}
