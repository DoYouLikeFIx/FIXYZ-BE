package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.vo.AccountPositionQueryCommand;
import com.fix.channel.vo.AccountPositionResult;
import com.fix.common.web.CorrelationIdSupport;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountPositionService {

  private final CorebankClient corebankClient;

  public AccountPositionResult getAccountPosition(AccountPositionQueryCommand command) {
    return corebankClient.getAccountPosition(command, CorrelationIdSupport.currentOrGenerate());
  }

  public AccountPositionResult getAccountSummary(Long accountId, Long memberId) {
    return corebankClient.getAccountSummary(accountId, memberId, CorrelationIdSupport.currentOrGenerate());
  }

  public List<AccountPositionResult> getAccountPositions(Long accountId, Long memberId) {
    return corebankClient.getAccountPositions(accountId, memberId, CorrelationIdSupport.currentOrGenerate());
  }
}
