package com.fix.fepgateway.controlplane.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.fepgateway.dataplane.fix.FixDataPlaneService;
import com.fix.fepgateway.entity.GatewayOrder;
import com.fix.fepgateway.entity.GatewayOrderCancel;
import com.fix.fepgateway.entity.GatewayOrderReplay;
import com.fix.fepgateway.repository.GatewayOrderCancelRepository;
import com.fix.fepgateway.repository.GatewayOrderReplayRepository;
import com.fix.fepgateway.repository.GatewayOrderRepository;
import com.fix.fepgateway.vo.GatewayOrderCancelCommand;
import com.fix.fepgateway.vo.GatewayInternalOrderStatusCommand;
import com.fix.fepgateway.vo.GatewayOrderReplayCommand;
import com.fix.fepgateway.vo.GatewayOrderResult;
import com.fix.fepgateway.vo.GatewayOrderStatusCommand;
import com.fix.fepgateway.vo.GatewayOrderSubmitCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FepGatewayControlService {

  private final GatewayOrderRepository gatewayOrderRepository;
  private final GatewayOrderCancelRepository gatewayOrderCancelRepository;
  private final GatewayOrderReplayRepository gatewayOrderReplayRepository;
  private final FixDataPlaneService fixDataPlaneService;

  public FepGatewayControlService(
      GatewayOrderRepository gatewayOrderRepository,
      GatewayOrderCancelRepository gatewayOrderCancelRepository,
      GatewayOrderReplayRepository gatewayOrderReplayRepository,
      FixDataPlaneService fixDataPlaneService
  ) {
    this.gatewayOrderRepository = gatewayOrderRepository;
    this.gatewayOrderCancelRepository = gatewayOrderCancelRepository;
    this.gatewayOrderReplayRepository = gatewayOrderReplayRepository;
    this.fixDataPlaneService = fixDataPlaneService;
  }

  @Transactional
  public GatewayOrderResult submitOrder(GatewayOrderSubmitCommand command) {
    GatewayOrder order = gatewayOrderRepository.findByClOrdId(command.getClOrdId())
        .orElseGet(() -> gatewayOrderRepository.save(
            GatewayOrder.received(command.getClOrdId(), command.getSymbol(), command.getSide(), command.getQty(), "FIX")
        ));
    String fixStatus = fixDataPlaneService.sendNewOrder(command);
    order.mark(fixStatus);
    return GatewayOrderResult.of(order.getClOrdId(), order.getStatus(), "CONTROL_PLANE_HTTP");
  }

  @Transactional(readOnly = true)
  public GatewayOrderResult status(GatewayOrderStatusCommand command) {
    GatewayOrder order = gatewayOrderRepository.findByClOrdId(command.getClOrdId())
        .orElseThrow(() -> new BusinessException(ErrorCode.FEP_GATEWAY_UNAVAILABLE, "order not found in gateway"));
    return GatewayOrderResult.of(order.getClOrdId(), order.getStatus(), "CONTROL_PLANE_HTTP");
  }

  @Transactional
  public GatewayOrderResult cancel(GatewayOrderCancelCommand command) {
    GatewayOrder order = gatewayOrderRepository.findByClOrdId(command.getClOrdId())
        .orElseThrow(() -> new BusinessException(ErrorCode.FEP_GATEWAY_UNAVAILABLE, "order not found in gateway"));

    gatewayOrderCancelRepository.save(GatewayOrderCancel.requested(command.getClOrdId(), command.getReason()));
    order.mark(fixDataPlaneService.sendCancel(command));
    return GatewayOrderResult.of(order.getClOrdId(), order.getStatus(), "CONTROL_PLANE_HTTP");
  }

  @Transactional
  public GatewayOrderResult replay(GatewayOrderReplayCommand command) {
    GatewayOrder order = gatewayOrderRepository.findByClOrdId(command.getClOrdId())
        .orElseThrow(() -> new BusinessException(ErrorCode.FEP_GATEWAY_UNAVAILABLE, "order not found in gateway"));

    gatewayOrderReplayRepository.save(GatewayOrderReplay.requested(command.getClOrdId(), command.getReason()));
    order.mark(fixDataPlaneService.sendReplay(command));
    return GatewayOrderResult.of(order.getClOrdId(), order.getStatus(), "CONTROL_PLANE_HTTP");
  }

  @Transactional
  public GatewayOrderResult internalUpdateStatus(GatewayInternalOrderStatusCommand command) {
    GatewayOrder order = gatewayOrderRepository.findByClOrdId(command.getClOrdId())
        .orElseThrow(() -> new BusinessException(ErrorCode.FEP_GATEWAY_UNAVAILABLE, "order not found in gateway"));

    order.mark(command.getStatus());
    return GatewayOrderResult.of(order.getClOrdId(), order.getStatus(), "CONTROL_PLANE_INTERNAL");
  }
}
