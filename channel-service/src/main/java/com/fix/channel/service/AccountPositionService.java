package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.vo.AccountPositionQueryCommand;
import com.fix.channel.vo.AccountPositionsQueryCommand;
import com.fix.channel.vo.AccountPositionResult;
import com.fix.channel.vo.AccountSummaryQueryCommand;
import java.util.List;
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

  public List<AccountPositionResult> getAccountPositions(AccountPositionsQueryCommand command) {
    return corebankClient.getAccountPositions(command, CorrelationIdSupport.currentOrGenerate());
  }

  public AccountPositionResult getAccountSummary(AccountSummaryQueryCommand command) {
    return corebankClient.getAccountSummary(command, CorrelationIdSupport.currentOrGenerate());
  }
}
