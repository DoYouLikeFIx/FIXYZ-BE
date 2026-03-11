package com.fix.channel.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
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
