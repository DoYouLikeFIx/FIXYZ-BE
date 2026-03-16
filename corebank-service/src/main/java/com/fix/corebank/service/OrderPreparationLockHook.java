package com.fix.corebank.service;

import org.springframework.stereotype.Component;

public interface OrderPreparationLockHook {

  void afterPositionLock(Long accountId, String symbol);
}

@Component
class NoOpOrderPreparationLockHook implements OrderPreparationLockHook {

  @Override
  public void afterPositionLock(Long accountId, String symbol) {
    // Default no-op hook. Tests can replace this bean to coordinate lock timing.
  }
}
