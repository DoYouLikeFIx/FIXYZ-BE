package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.support.ChannelCorrelationIdSupport;
import com.fix.channel.vo.AccountOrderHistoryQueryCommand;
import com.fix.channel.vo.AccountOrderHistoryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountOrderHistoryService {

  private final CorebankClient corebankClient;

  public AccountOrderHistoryResult getAccountOrderHistory(AccountOrderHistoryQueryCommand command) {
    return corebankClient.getAccountOrderHistory(command, ChannelCorrelationIdSupport.currentOrGenerate());
  }
}
