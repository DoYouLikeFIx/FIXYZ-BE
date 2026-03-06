package com.fix.fepgateway.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "gateway_orders")
public class GatewayOrder extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cl_ord_id", nullable = false, unique = true, length = 64)
  private String clOrdId;

  @Column(name = "symbol", nullable = false, length = 32)
  private String symbol;

  @Column(name = "side", nullable = false, length = 8)
  private String side;

  @Column(name = "qty", nullable = false, precision = 19, scale = 4)
  private BigDecimal qty;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "transport", nullable = false, length = 16)
  private String transport;

  protected GatewayOrder() {
  }

  private GatewayOrder(String clOrdId, String symbol, String side, BigDecimal qty, String status, String transport) {
    this.clOrdId = clOrdId;
    this.symbol = symbol;
    this.side = side;
    this.qty = qty;
    this.status = status;
    this.transport = transport;
  }

  public static GatewayOrder received(String clOrdId, String symbol, String side, BigDecimal qty, String transport) {
    return new GatewayOrder(clOrdId, symbol, side, qty, "RECEIVED", transport);
  }

  public Long getId() {
    return id;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getSide() {
    return side;
  }

  public BigDecimal getQty() {
    return qty;
  }

  public String getStatus() {
    return status;
  }

  public String getTransport() {
    return transport;
  }

  public void mark(String status) {
    this.status = status;
  }
}
