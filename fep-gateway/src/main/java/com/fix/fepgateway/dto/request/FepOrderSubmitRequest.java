package com.fix.fepgateway.dto.request;

import com.fix.fepgateway.vo.GatewayOrderSubmitCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class FepOrderSubmitRequest {

  @NotBlank
  private String clOrdId;

  @NotBlank
  private String symbol;

  @NotBlank
  private String side;

  @NotNull
  @DecimalMin("0.0001")
  private BigDecimal qty;

  public GatewayOrderSubmitCommand toVo() {
    return GatewayOrderSubmitCommand.of(clOrdId, symbol, side, qty);
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

  public BigDecimal getQty() {
    return qty;
  }

  public void setQty(BigDecimal qty) {
    this.qty = qty;
  }
}
