package com.fix.channel.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
    value = "order.session.recovery.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class OrderSessionRecoveryScheduler {

  private final OrderSessionRecoveryService orderSessionRecoveryService;

  @Scheduled(fixedDelayString = "${order.session.recovery.fixed-delay-ms:60000}")
  public void runRecoveryCycle() {
    try {
      orderSessionRecoveryService.runRecoveryCycle();
    } catch (RuntimeException ex) {
      log.warn("Order session recovery cycle failed", ex);
    }
  }
}
