package com.fix.corebank.service;

import com.fix.corebank.entity.Account;
import com.fix.corebank.entity.Order;
import com.fix.corebank.entity.Position;
import org.springframework.stereotype.Component;

public interface OrderPostingTransactionHook {

  void afterPostingMutation(Order order, Account account, Position position);
}

@Component
class NoOpOrderPostingTransactionHook implements OrderPostingTransactionHook {

  @Override
  public void afterPostingMutation(Order order, Account account, Position position) {
    // Default no-op hook. Tests can replace this bean to assert rollback behavior.
  }
}
