package com.fix.corebank.dto.request;

import com.fix.corebank.vo.InternalOrderCreateCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class InternalOrderCreateRequest {

  @NotNull
  private Long accountId;

  @NotBlank
  private String clOrdId;

  @NotBlank
  private String symbol;

  @NotBlank
  private String side;

  @NotNull
  @DecimalMin("0.0001")
  private BigDecimal quantity;

  @NotNull
  @DecimalMin("0.0001")
  private BigDecimal price;

  public InternalOrderCreateCommand toVo() {
    return InternalOrderCreateCommand.of(accountId, clOrdId, symbol, side, quantity, price);
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
}
