package com.fix.channel.dto.request;

import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.common.validation.ContractPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.Locale;

public record OrderSessionCreateRequest(
    @NotNull
    Long accountId,

    @NotBlank(message = "symbol is required")
    @Pattern(regexp = ContractPatterns.DOMESTIC_ORDER_SYMBOL, message = "symbol must be 6 digits or Q + 6 digits")
    String symbol,

    @NotBlank(message = "side is required")
    @Pattern(regexp = "^(BUY|SELL)$", message = "side must be BUY or SELL")
    String side,

    @NotBlank(message = "orderType is required")
    @Pattern(regexp = "^(LIMIT|MARKET)$", message = "orderType must be LIMIT or MARKET")
    String orderType,

    @NotNull
    @DecimalMin(value = "1", message = "qty must be at least 1")
    BigDecimal qty,

    @DecimalMin(value = "1", message = "price must be at least 1")
    BigDecimal price
) {

  @Schema(hidden = true)
  @AssertTrue(message = "qty must be a whole number")
  public boolean isQtyWholeNumber() {
    return isWholeNumber(qty);
  }

  @Schema(hidden = true)
  @AssertTrue(message = "price must be a whole number when provided")
  public boolean isPriceWholeNumber() {
    return price == null || isWholeNumber(price);
  }

  @Schema(hidden = true)
  @AssertTrue(message = "LIMIT orders require price and MARKET orders must omit price")
  public boolean isPriceContractValid() {
    if (orderType == null || orderType.isBlank()) {
      return true;
    }
    return switch (orderType.trim().toUpperCase(Locale.ROOT)) {
      case "LIMIT" -> price != null;
      case "MARKET" -> price == null;
      default -> true;
    };
  }

  public OrderSessionCreateCommand toVo(Long memberId, String clOrdId) {
    return OrderSessionCreateCommand.of(
        memberId,
        accountId,
        clOrdId,
        symbol.trim().toUpperCase(Locale.ROOT),
        side.trim().toUpperCase(Locale.ROOT),
        orderType.trim().toUpperCase(Locale.ROOT),
        normalizeWholeNumber(qty),
        price == null ? null : normalizeWholeNumber(price)
    );
  }

  private boolean isWholeNumber(BigDecimal value) {
    return value == null || value.stripTrailingZeros().scale() <= 0;
  }

  private BigDecimal normalizeWholeNumber(BigDecimal value) {
    BigDecimal normalized = value.stripTrailingZeros();
    return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
  }
}
