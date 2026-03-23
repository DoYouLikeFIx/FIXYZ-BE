package com.fix.corebank.dto.request;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.common.validation.ContractPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

public class InternalOrderCreateRequest {

  private static final String LIMIT_ORDER_TYPE = "LIMIT";
  private static final String MARKET_ORDER_TYPE = "MARKET";

  @NotNull
  private Long accountId;

  @NotBlank
  @Pattern(regexp = ContractPatterns.UUID_V4)
  private String clOrdId;

  @NotBlank
  private String symbol;

  @NotBlank
  private String side;

  @NotNull
  @DecimalMin("0.0001")
  private BigDecimal quantity;

  @DecimalMin("0.0001")
  private BigDecimal price;

  private String orderType;

  private String quoteSnapshotId;

  private Instant quoteAsOf;

  private FepQuoteSourceMode quoteSourceMode;

  @DecimalMin("0.0001")
  private BigDecimal preTradePrice;

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

  @Schema(hidden = true)
  @AssertTrue(message = "preTradePrice must be a whole number")
  public boolean isPreTradePriceWholeNumber() {
    return isWholeNumber(preTradePrice);
  }

  @Schema(hidden = true)
  @AssertTrue(message = "orderType contract is invalid")
  public boolean isOrderTypeContractValid() {
    String normalizedOrderType = normalizedOrderType();
    if (!LIMIT_ORDER_TYPE.equals(normalizedOrderType) && !MARKET_ORDER_TYPE.equals(normalizedOrderType)) {
      return false;
    }
    if (LIMIT_ORDER_TYPE.equals(normalizedOrderType)) {
      return price != null;
    }
    return price == null
        && quoteSnapshotId != null
        && !quoteSnapshotId.isBlank()
        && quoteAsOf != null
        && quoteSourceMode != null
        && preTradePrice != null;
  }

  public InternalOrderCreateCommand toVo() {
    return InternalOrderCreateCommand.of(
        accountId,
        clOrdId,
        symbol,
        side,
        normalizedOrderType(),
        quantity,
        price,
        quoteSnapshotId,
        quoteAsOf,
        quoteSourceMode,
        preTradePrice
    );
  }

  public Long getAccountId() {
    return accountId;
  }

  public void setAccountId(Long accountId) {
    this.accountId = accountId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public void setClOrdId(String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public String getSide() {
    return side;
  }

  public void setSide(String side) {
    this.side = side;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public void setQuantity(BigDecimal quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getOrderType() {
    return orderType;
  }

  public void setOrderType(String orderType) {
    this.orderType = orderType;
  }

  public String getQuoteSnapshotId() {
    return quoteSnapshotId;
  }

  public void setQuoteSnapshotId(String quoteSnapshotId) {
    this.quoteSnapshotId = quoteSnapshotId;
  }

  public Instant getQuoteAsOf() {
    return quoteAsOf;
  }

  public void setQuoteAsOf(Instant quoteAsOf) {
    this.quoteAsOf = quoteAsOf;
  }

  public FepQuoteSourceMode getQuoteSourceMode() {
    return quoteSourceMode;
  }

  public void setQuoteSourceMode(FepQuoteSourceMode quoteSourceMode) {
    this.quoteSourceMode = quoteSourceMode;
  }

  public BigDecimal getPreTradePrice() {
    return preTradePrice;
  }

  public void setPreTradePrice(BigDecimal preTradePrice) {
    this.preTradePrice = preTradePrice;
  }

  private boolean isWholeNumber(BigDecimal value) {
    return value == null || value.stripTrailingZeros().scale() <= 0;
  }

  private String normalizedOrderType() {
    if (orderType == null || orderType.isBlank()) {
      return LIMIT_ORDER_TYPE;
    }
    return orderType.trim().toUpperCase(Locale.ROOT);
  }
}
