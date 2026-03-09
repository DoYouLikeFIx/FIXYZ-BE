package com.fix.fepgateway.dto.request;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
import com.fix.common.validation.ContractPatterns;
import com.fix.fepgateway.vo.GatewayOrderSubmitCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record FepOrderSubmitRequest(
    @NotBlank
    @Pattern(regexp = ContractPatterns.UUID_V4)
    String clOrdId,
    @NotBlank String accountId,
    @NotBlank
    @Pattern(regexp = ContractPatterns.SIX_DIGIT_SYMBOL)
    String symbol,
    @NotNull FepSecurityExchange securityExchange,
    @NotNull FepSide side,
    @NotNull FepOrderType orderType,
    @NotNull @Positive Long qty,
    @Positive Long price,
    String quoteSnapshotId,
    Instant quoteAsOf,
    FepQuoteSourceMode quoteSourceMode,
    @Positive Long preTradePrice,
    @NotBlank String currency,
    @NotBlank String referenceId
) {

  public GatewayOrderSubmitCommand toVo() {
    validateContract();
    return new GatewayOrderSubmitCommand(
        clOrdId,
        accountId,
        symbol,
        securityExchange,
        side,
        orderType,
        qty,
        price,
        quoteSnapshotId,
        quoteAsOf,
        quoteSourceMode,
        preTradePrice,
        currency,
        referenceId
    );
  }

  private void validateContract() {
    if (orderType == FepOrderType.LIMIT && price == null) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "price is required for LIMIT orders");
    }

    if (orderType == FepOrderType.MARKET) {
      if (price != null) {
        throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "price must be omitted for MARKET orders");
      }
      if (isBlank(quoteSnapshotId) || quoteAsOf == null || quoteSourceMode == null || preTradePrice == null) {
        throw new BusinessException(
            ErrorCode.CONTRACT_VALIDATION_FAILED,
            "market orders require quoteSnapshotId, quoteAsOf, quoteSourceMode, and preTradePrice"
        );
      }
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
