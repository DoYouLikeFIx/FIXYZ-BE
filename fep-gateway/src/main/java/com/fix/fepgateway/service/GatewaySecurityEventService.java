package com.fix.fepgateway.service;

import com.fix.common.web.CorrelationIdSupport;
import com.fix.fepgateway.entity.GatewayOrder;
import com.fix.fepgateway.entity.GatewaySecurityEvent;
import com.fix.fepgateway.repository.GatewaySecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewaySecurityEventService {

  private final GatewaySecurityEventRepository gatewaySecurityEventRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordDeniedReplay(
      String eventType,
      String referenceId,
      GatewayOrder ownerOrder,
      String attemptedAccountId,
      String attemptedClOrdId,
      String detail
  ) {
    gatewaySecurityEventRepository.save(GatewaySecurityEvent.deniedReplay(
        eventType,
        referenceId,
        ownerOrder != null ? ownerOrder.getAccountId() : null,
        attemptedAccountId,
        ownerOrder != null ? ownerOrder.getClOrdId() : null,
        attemptedClOrdId,
        CorrelationIdSupport.currentOrGenerate(),
        detail
    ));
  }
}
