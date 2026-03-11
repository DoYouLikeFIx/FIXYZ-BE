package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.vo.AccountPositionQueryCommand;
import com.fix.channel.vo.AccountPositionResult;
import com.fix.common.web.CorrelationIdSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountPositionService {

  private final CorebankClient corebankClient;

  public AccountPositionResult getAccountPosition(AccountPositionQueryCommand command) {
    return corebankClient.getAccountPosition(command, CorrelationIdSupport.currentOrGenerate());
  }
}
