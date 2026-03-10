package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.vo.OrderExecuteCommand;
import com.fix.channel.vo.OrderExecuteResult;
import com.fix.common.web.CorrelationIdSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderExecutionService {

  private final CorebankClient corebankClient;

  public OrderExecuteResult execute(OrderExecuteCommand command) {
    return corebankClient.executeOrder(command, CorrelationIdSupport.currentOrGenerate());
  }
}
