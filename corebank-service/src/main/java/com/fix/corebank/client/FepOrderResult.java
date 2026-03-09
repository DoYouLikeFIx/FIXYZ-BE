package com.fix.corebank.client;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import java.time.Instant;

public record FepOrderResult(
    String clOrdId,
    String fepOrderId,
    FepExecType execType,
    FepOrdStatus ordStatus,
    Long executedQty,
    Long executedPrice,
    Long leavesQty,
    Instant transactTime,
    Instant queryTime,
    String message
) {

  public static FepOrderResult fromSubmitResponse(FepGatewayOrderResponse response, String expectedClOrdId) {
    require(!isBlank(response.clOrdId()), "clOrdId is required in submit response");
    require(expectedClOrdId.equals(response.clOrdId()), "submit response clOrdId must match request");
    require(!isBlank(response.fepOrderId()), "fepOrderId is required in submit response");
    require(response.execType() != null, "execType is required in submit response");
    require(response.ordStatus() != null, "ordStatus is required in submit response");
    require(response.leavesQty() != null, "leavesQty is required in submit response");
    require(response.transactTime() != null, "transactTime is required in submit response");
    return new FepOrderResult(
        response.clOrdId(),
        response.fepOrderId(),
        response.execType(),
        response.ordStatus(),
        response.executedQty(),
        response.executedPrice(),
        response.leavesQty(),
        response.transactTime(),
        response.queryTime(),
        response.message()
    );
  }

  public static FepOrderResult fromStatusResponse(FepGatewayOrderResponse response, String expectedClOrdId) {
    require(!isBlank(response.clOrdId()), "clOrdId is required in status response");
    require(expectedClOrdId.equals(response.clOrdId()), "status response clOrdId must match request");
    require(response.ordStatus() != null, "ordStatus is required in status response");
    require(response.queryTime() != null, "queryTime is required in status response");

    if (response.ordStatus() == FepOrdStatus.UNKNOWN) {
      require(!isBlank(response.message()), "message is required when ordStatus is UNKNOWN");
    } else {
      require(response.execType() != null, "execType is required unless ordStatus is UNKNOWN");
    }

    return new FepOrderResult(
        response.clOrdId(),
        response.fepOrderId(),
        response.execType(),
        response.ordStatus(),
        response.executedQty(),
        response.executedPrice(),
        response.leavesQty(),
        response.transactTime(),
        response.queryTime(),
        response.message()
    );
  }

  private static void require(boolean expression, String message) {
    if (!expression) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, message);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
