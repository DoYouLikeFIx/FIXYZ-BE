package com.fix.channel.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class PasswordRecoveryConfig {

  @Bean
  public Clock passwordRecoveryClock() {
    return Clock.systemUTC();
  }

  @Bean(name = "passwordRecoveryTaskExecutor")
  public TaskExecutor passwordRecoveryTaskExecutor() {
    return new SimpleAsyncTaskExecutor("password-recovery-");
  }

  @Bean("passwordRecoveryCleanupCadenceMillis")
  public long passwordRecoveryCleanupCadenceMillis(PasswordRecoveryProperties properties) {
    return properties.getCleanup().getCadence().toMillis();
  }
}
