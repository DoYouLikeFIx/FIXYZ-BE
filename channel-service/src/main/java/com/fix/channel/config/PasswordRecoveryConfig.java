package com.fix.channel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class PasswordRecoveryConfig {

  @Bean(name = "passwordRecoveryTaskExecutor")
  public TaskExecutor passwordRecoveryTaskExecutor() {
    return new SimpleAsyncTaskExecutor("password-recovery-");
  }
}
