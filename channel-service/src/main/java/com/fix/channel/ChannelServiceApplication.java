package com.fix.channel;

import com.fix.channel.config.PasswordRecoveryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PasswordRecoveryProperties.class)
public class ChannelServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ChannelServiceApplication.class, args);
  }
}
