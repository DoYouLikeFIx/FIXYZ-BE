package com.fix.channel.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    value = "auth.password-recovery.cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class PasswordRecoveryCleanupScheduler {

  private final PasswordRecoveryCleanupService passwordRecoveryCleanupService;

  public PasswordRecoveryCleanupScheduler(PasswordRecoveryCleanupService passwordRecoveryCleanupService) {
    this.passwordRecoveryCleanupService = passwordRecoveryCleanupService;
  }

  @Scheduled(
      fixedRateString = "#{@passwordRecoveryCleanupCadenceMillis}",
      initialDelayString = "#{@passwordRecoveryCleanupCadenceMillis}"
  )
  public void cleanUpPasswordResetTokens() {
    passwordRecoveryCleanupService.runCleanupCycle();
  }
}
