package com.fix.fepgateway.dto.request;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepSide;
import com.fix.common.validation.ContractPatterns;
import com.fix.fepgateway.vo.GatewayOrderCancelCommand;
import com.fix.fepgateway.vo.FepCancelReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record FepOrderCancelRequest(
    @NotBlank
    @Pattern(regexp = ContractPatterns.UUID_V4)
    String origClOrdId,
    @NotBlank
    @Pattern(regexp = ContractPatterns.SIX_DIGIT_SYMBOL)
    String symbol,
    @NotNull FepSide side,
    @NotNull @Positive Long cancelQty,
    @NotNull FepCancelReason reason
) {

  public GatewayOrderCancelCommand toVo(String pathClOrdId) {
    if (!pathClOrdId.equals(origClOrdId)) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "origClOrdId must match path clOrdId");
    }
    return GatewayOrderCancelCommand.of(origClOrdId, symbol, side, cancelQty, reason);
  }
}
