package com.fix.fepgateway.dataplane.fix;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepExecutionResult;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.fep.FepReplayExecutionSource;
import com.fix.common.fep.FepReplayFinalStatus;
import com.fix.fepgateway.entity.GatewayOrder;
import com.fix.fepgateway.vo.GatewayOrderCancelCommand;
import com.fix.fepgateway.vo.GatewayExecutionOutcome;
import com.fix.fepgateway.vo.GatewayOrderResult;
import com.fix.fepgateway.vo.GatewayReplayExecution;
import com.fix.fepgateway.vo.GatewayOrderReplayCommand;
import com.fix.fepgateway.vo.GatewayOrderSubmitCommand;
import com.fix.fepgateway.vo.FepReplayDecision;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class FixDataPlaneService {

  private static final String CHAOS_ACTION_NONE = "NONE";

  private final RestClient simulatorRestClient;
  private final boolean chaosProbeEnabled;

  @Autowired
  public FixDataPlaneService(
      RestClient.Builder restClientBuilder,
      @Value("${fep.simulator.base-url:http://localhost:8082}") String simulatorBaseUrl,
      @Value("${fep.simulator.chaos-probe-enabled:false}") boolean chaosProbeEnabled
  ) {
    this(restClientBuilder.baseUrl(simulatorBaseUrl).build(), chaosProbeEnabled);
  }

  protected FixDataPlaneService() {
    this(null, false);
  }

  protected FixDataPlaneService(RestClient simulatorRestClient, boolean chaosProbeEnabled) {
    this.simulatorRestClient = simulatorRestClient;
    this.chaosProbeEnabled = chaosProbeEnabled;
  }

  public GatewayOrderResult sendOrderStatusRequest(String clOrdId, GatewayOrder order) {
    if (order == null) {
      return new GatewayOrderResult(
          clOrdId,
          null,
          null,
          FepOrdStatus.UNKNOWN,
          null,
          null,
          null,
          null,
          Instant.now(),
          "external system does not have a matching order",
          null,
          null,
          null
      );
    }
    return order.toResult(Instant.now());
  }

  public GatewayExecutionOutcome sendNewOrder(GatewayOrderSubmitCommand command) {
    long executedPrice = resolveSubmitPrice(command);
    String chaosAction = resolveSubmitChaosAction(command, executedPrice);
    if ("TIMEOUT".equals(chaosAction)) {
      throw new BusinessException(
          ErrorCode.FEP_ACK_TIMEOUT,
          "submit acknowledgement timed out",
          new com.fix.common.error.ErrorMetadata("error.fep.timeout", "TIMEOUT")
      );
    }
    return new GatewayExecutionOutcome(
        "FEP-%s-%s".formatted(command.securityExchange().name(), command.clOrdId()),
        FepExecType.FILL,
        FepOrdStatus.FILLED,
        command.qty(),
        executedPrice,
        0L,
        Instant.now(),
        null,
        null,
        null
    );
  }

  private long resolveSubmitPrice(GatewayOrderSubmitCommand command) {
    return command.orderType().name().equals("LIMIT") ? command.price() : command.preTradePrice();
  }

  private String resolveSubmitChaosAction(GatewayOrderSubmitCommand command, long executedPrice) {
    if (!chaosProbeEnabled || simulatorRestClient == null) {
      return CHAOS_ACTION_NONE;
    }

    try {
      long amount = Math.multiplyExact(command.qty(), executedPrice);
      SimulatorPingResponse response = simulatorRestClient.get()
          .uri(uriBuilder -> uriBuilder.path("/api/v1/ping")
              .queryParam("symbol", command.symbol())
              .queryParam("exchange", command.securityExchange().name())
              .queryParam("amount", amount)
              .build())
          .retrieve()
          .body(new ParameterizedTypeReference<SimulatorPingResponse>() {
          });
      if (response == null || response.chaosAction() == null || response.chaosAction().isBlank()) {
        return CHAOS_ACTION_NONE;
      }
      return response.chaosAction().trim().toUpperCase(Locale.ROOT);
    } catch (ArithmeticException | RestClientException ex) {
      return CHAOS_ACTION_NONE;
    }
  }

  public GatewayExecutionOutcome sendCancel(GatewayOrderCancelCommand command, GatewayOrder order) {
    if ("TIMEOUT".equals(order.getCancelFailureMode())) {
      throw new BusinessException(ErrorCode.FEP_ACK_TIMEOUT, "cancel acknowledgement timed out");
    }
    if ("REJECT".equals(order.getCancelFailureMode())) {
      throw new BusinessException(ErrorCode.CANCEL_REJECTED, "exchange rejected cancel request");
    }

    long executedQty = order.getExecutedQty() == null ? 0L : order.getExecutedQty();
    Long executedPrice = order.getExecutedPrice() != null ? order.getExecutedPrice() : order.referencePrice();
    long leavesAfterCancel = Math.max(order.remainingQty() - command.getCancelQty(), 0L);
    FepOrdStatus nextStatus = leavesAfterCancel == 0
        ? FepOrdStatus.CANCELED
        : (executedQty > 0 ? FepOrdStatus.PARTIALLY_FILLED : FepOrdStatus.PENDING);
    return new GatewayExecutionOutcome(
        order.getFepOrderId(),
        FepExecType.CANCELED,
        nextStatus,
        executedQty,
        executedQty > 0 ? executedPrice : null,
        leavesAfterCancel,
        Instant.now(),
        null,
        null,
        null
    );
  }

  public GatewayReplayExecution sendReplay(GatewayOrderReplayCommand command, GatewayOrder order) {
    long totalQty = order.totalQty();
    long executedQty = order.getExecutedQty() == null ? 0L : order.getExecutedQty();

    if (command.getManualDecision() == FepReplayDecision.REJECT) {
      return new GatewayReplayExecution(
          new GatewayExecutionOutcome(
              order.getFepOrderId(),
              FepExecType.REJECTED,
              FepOrdStatus.REJECTED,
              0L,
              null,
              0L,
              Instant.now(),
              null,
              null,
              null
          ),
          FepReplayFinalStatus.FAILED,
          null,
          null
      );
    }

    if (FepOrdStatus.REJECTED.name().equals(order.getStatus())) {
      return new GatewayReplayExecution(
          new GatewayExecutionOutcome(
              order.getFepOrderId(),
              FepExecType.REJECTED,
              FepOrdStatus.REJECTED,
              0L,
              null,
              0L,
              Instant.now(),
              null,
              null,
              null
          ),
          FepReplayFinalStatus.FAILED,
          null,
          null
      );
    }

    if (FepOrdStatus.FILLED.name().equals(order.getStatus())) {
      return new GatewayReplayExecution(
          new GatewayExecutionOutcome(
              order.getFepOrderId(),
              FepExecType.FILL,
              FepOrdStatus.FILLED,
              totalQty,
              order.getExecutedPrice() != null ? order.getExecutedPrice() : order.getRequestedPrice(),
              0L,
              Instant.now(),
              null,
              null,
              null
          ),
          FepReplayFinalStatus.COMPLETED,
          null,
          FepReplayExecutionSource.FILLED
      );
    }

    if (FepOrdStatus.PARTIALLY_FILLED.name().equals(order.getStatus())) {
      return new GatewayReplayExecution(
          new GatewayExecutionOutcome(
              order.getFepOrderId(),
              FepExecType.PARTIAL_FILL,
              FepOrdStatus.PARTIALLY_FILLED,
              executedQty,
              order.getExecutedPrice() != null ? order.getExecutedPrice() : order.getRequestedPrice(),
              order.getLeavesQty(),
              Instant.now(),
              null,
              null,
              null
          ),
          FepReplayFinalStatus.COMPLETED,
          null,
          FepReplayExecutionSource.FILLED
      );
    }

    if (FepOrdStatus.CANCELED.name().equals(order.getStatus())) {
      return new GatewayReplayExecution(
          new GatewayExecutionOutcome(
              order.getFepOrderId(),
              FepExecType.CANCELED,
              FepOrdStatus.CANCELED,
              executedQty,
              order.getExecutedPrice(),
              0L,
              Instant.now(),
              null,
              null,
              null
          ),
          FepReplayFinalStatus.CANCELED,
          executedQty > 0 ? FepExecutionResult.PARTIAL_FILL_CANCEL : null,
          null
      );
    }

    if (FepOrdStatus.UNKNOWN.name().equals(order.getStatus())
        || FepOrdStatus.PENDING.name().equals(order.getStatus())
        || FepOrdStatus.MALFORMED.name().equals(order.getStatus())) {
      if (order.hasRequeryOutcome()) {
        return resolveRequeryOutcome(command, order, totalQty);
      }
      return buildVirtualFillReplay(command, order, totalQty);
    }

    return new GatewayReplayExecution(
        new GatewayExecutionOutcome(
            order.getFepOrderId(),
            FepExecType.FILL,
            FepOrdStatus.FILLED,
            totalQty,
            order.getRequestedPrice(),
            0L,
            Instant.now(),
            null,
            null,
            null
        ),
        FepReplayFinalStatus.COMPLETED,
        null,
        FepReplayExecutionSource.FILLED
    );
  }

  private GatewayReplayExecution resolveRequeryOutcome(
      GatewayOrderReplayCommand command,
      GatewayOrder order,
      long totalQty
  ) {
    FepOrdStatus requeryStatus = FepOrdStatus.valueOf(order.getRequeryOrdStatus());
    long requeryExecutedQty = order.getRequeryExecutedQty() == null ? 0L : order.getRequeryExecutedQty();
    Long requeryExecutedPrice = order.getRequeryExecutedPrice() != null
        ? order.getRequeryExecutedPrice()
        : order.referencePrice();

    return switch (requeryStatus) {
      case FILLED -> new GatewayReplayExecution(
          new GatewayExecutionOutcome(
              order.getFepOrderId(),
              FepExecType.FILL,
              FepOrdStatus.FILLED,
              totalQty,
              requeryExecutedPrice,
              0L,
              Instant.now(),
              null,
              null,
              null
          ),
          FepReplayFinalStatus.COMPLETED,
          null,
          FepReplayExecutionSource.FILLED
      );
      case PARTIALLY_FILLED -> new GatewayReplayExecution(
          new GatewayExecutionOutcome(
              order.getFepOrderId(),
              FepExecType.PARTIAL_FILL,
              FepOrdStatus.PARTIALLY_FILLED,
              requeryExecutedQty,
              requeryExecutedPrice,
              Math.max(totalQty - requeryExecutedQty, 0L),
              Instant.now(),
              null,
              null,
              null
          ),
          FepReplayFinalStatus.COMPLETED,
          null,
          FepReplayExecutionSource.FILLED
      );
      case CANCELED -> new GatewayReplayExecution(
          new GatewayExecutionOutcome(
              order.getFepOrderId(),
              FepExecType.CANCELED,
              FepOrdStatus.CANCELED,
              requeryExecutedQty,
              requeryExecutedQty > 0 ? requeryExecutedPrice : null,
              0L,
              Instant.now(),
              null,
              null,
              null
          ),
          FepReplayFinalStatus.CANCELED,
          requeryExecutedQty > 0 ? FepExecutionResult.PARTIAL_FILL_CANCEL : null,
          null
      );
      case REJECTED -> new GatewayReplayExecution(
          new GatewayExecutionOutcome(
              order.getFepOrderId(),
              FepExecType.REJECTED,
              FepOrdStatus.REJECTED,
              0L,
              null,
              0L,
              Instant.now(),
              null,
              null,
              null
          ),
          FepReplayFinalStatus.FAILED,
          null,
          null
      );
      case UNKNOWN, PENDING, MALFORMED -> buildVirtualFillReplay(command, order, totalQty);
    };
  }

  private GatewayReplayExecution buildVirtualFillReplay(
      GatewayOrderReplayCommand command,
      GatewayOrder order,
      long totalQty
  ) {
    long executedPrice = resolveVirtualFillPrice(command, order);
    return new GatewayReplayExecution(
        new GatewayExecutionOutcome(
            order.getFepOrderId(),
            FepExecType.FILL,
            FepOrdStatus.FILLED,
            totalQty,
            executedPrice,
            0L,
            Instant.now(),
            null,
            null,
            null
        ),
        FepReplayFinalStatus.COMPLETED,
        null,
        FepReplayExecutionSource.VIRTUAL_FILL
    );
  }

  private long resolveVirtualFillPrice(GatewayOrderReplayCommand command, GatewayOrder order) {
    if (order.isMarketOrder()) {
      Long executionPrice = command.getExecutionPrice();
      if (executionPrice == null || executionPrice <= 0) {
        throw new BusinessException(
            ErrorCode.CONTRACT_VALIDATION_FAILED,
            "executionPrice is required for MARKET virtual fill replay"
        );
      }
      return executionPrice;
    }

    Long referencePrice = order.referencePrice();
    if (referencePrice == null || referencePrice <= 0) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "reference price is required for virtual fill replay"
      );
    }
    return referencePrice;
  }

  private record SimulatorPingResponse(String chaosAction) {
  }
}
