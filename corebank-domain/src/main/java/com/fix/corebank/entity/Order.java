package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "cl_ord_id", nullable = false, unique = true, length = 64)
  private String clOrdId;

  @Column(name = "symbol", nullable = false, length = 32)
  private String symbol;

  @Column(name = "side", nullable = false, length = 8)
  private String side;

  @Column(name = "order_qty", nullable = false, precision = 19, scale = 4)
  private BigDecimal orderQty;

  @Column(name = "order_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal orderPrice;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  protected Order() {
  }

  private Order(
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal orderQty,
      BigDecimal orderPrice,
      String status,
      Instant requestedAt
  ) {
    this.accountId = accountId;
    this.clOrdId = clOrdId;
    this.symbol = symbol;
    this.side = side;
    this.orderQty = orderQty;
    this.orderPrice = orderPrice;
    this.status = status;
    this.requestedAt = requestedAt;
  }

  public static Order accepted(
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal orderQty,
      BigDecimal orderPrice
  ) {
    return new Order(accountId, clOrdId, symbol, side, orderQty, orderPrice, "ACCEPTED", Instant.now());
  }

  public Long getId() {
    return id;
  }

  public Long getAccountId() {
    return accountId;
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

  public BigDecimal getOrderQty() {
    return orderQty;
  }

  public BigDecimal getOrderPrice() {
    return orderPrice;
  }

  public String getStatus() {
    return status;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }
}
