package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.vo.AccountOrderHistoryQueryCommand;
import com.fix.channel.vo.AccountOrderHistoryResult;
import com.fix.common.web.CorrelationIdSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountOrderHistoryService {

  private final CorebankClient corebankClient;

  public AccountOrderHistoryResult getAccountOrderHistory(AccountOrderHistoryQueryCommand command) {
    return corebankClient.getAccountOrderHistory(command, CorrelationIdSupport.currentOrGenerate());
  }
}
