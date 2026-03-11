package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.vo.AdminAccountStatusTransitionCommand;
import com.fix.channel.vo.AdminAccountStatusTransitionResult;
import com.fix.common.web.CorrelationIdSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAccountStatusService {

  private final CorebankClient corebankClient;

  public AdminAccountStatusTransitionResult transitionAccountStatus(AdminAccountStatusTransitionCommand command) {
    return corebankClient.transitionAccountStatus(command, CorrelationIdSupport.currentOrGenerate());
  }
}
