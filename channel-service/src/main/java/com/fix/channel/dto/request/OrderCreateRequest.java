package com.fix.channel.dto.request;

import com.fix.channel.vo.OrderExecuteCommand;
import com.fix.common.validation.ContractPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record OrderCreateRequest(
    @NotNull
    Long accountId,

    @NotBlank
    @Pattern(regexp = ContractPatterns.UUID_V4)
    String clOrdId,

    @NotBlank
    String symbol,

    @NotBlank
    String side,

    @NotNull
    @DecimalMin("0.0001")
    BigDecimal quantity,

    @NotNull
    @DecimalMin("0.0001")
    BigDecimal price
) {

  @Schema(hidden = true)
  @AssertTrue(message = "quantity must be a whole number")
  public boolean isQuantityWholeNumber() {
    return isWholeNumber(quantity);
  }

  @Schema(hidden = true)
  @AssertTrue(message = "price must be a whole number")
  public boolean isPriceWholeNumber() {
    return isWholeNumber(price);
  }

  public OrderExecuteCommand toVo() {
    return OrderExecuteCommand.of(accountId, clOrdId, symbol, side, quantity, price);
  }

  private boolean isWholeNumber(BigDecimal value) {
    return value == null || value.stripTrailingZeros().scale() <= 0;
  }
}
