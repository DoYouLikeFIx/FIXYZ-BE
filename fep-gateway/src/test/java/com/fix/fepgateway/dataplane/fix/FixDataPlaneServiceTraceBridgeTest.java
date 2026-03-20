package com.fix.fepgateway.dataplane.fix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
import com.fix.fepgateway.vo.GatewayExecutionOutcome;
import com.fix.fepgateway.vo.GatewayOrderSubmitCommand;
import org.junit.jupiter.api.Test;

class FixDataPlaneServiceTraceBridgeTest {

  @Test
  void shouldKeepSendNewOrderDeterministicWhenTraceBridgeThrows() {
    FepSimulatorTraceBridgeClient bridgeClient = mock(FepSimulatorTraceBridgeClient.class);
    doThrow(new IllegalStateException("bridge down")).when(bridgeClient).bridgeCurrentTrace();

    FixDataPlaneService service = new FixDataPlaneService(bridgeClient);

    GatewayExecutionOutcome outcome = service.sendNewOrder(new GatewayOrderSubmitCommand(
        "cl-ord-83",
        "ACC-001",
        "005930",
        FepSecurityExchange.KRX,
        FepSide.BUY,
        FepOrderType.LIMIT,
        10L,
        72000L,
        null,
        null,
        FepQuoteSourceMode.LIVE,
        71900L,
        "KRW",
        "ref-83"
    ));

    assertThat(outcome.fepOrderId()).isEqualTo("FEP-KRX-cl-ord-83");
    assertThat(outcome.execType()).isEqualTo(FepExecType.FILL);
    assertThat(outcome.ordStatus()).isEqualTo(FepOrdStatus.FILLED);
    assertThat(outcome.executedQty()).isEqualTo(10L);
    assertThat(outcome.executedPrice()).isEqualTo(72000L);
    verify(bridgeClient).bridgeCurrentTrace();
  }
}
