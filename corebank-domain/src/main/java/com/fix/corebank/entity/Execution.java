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
@Table(name = "executions")
public class Execution extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "cl_ord_id", nullable = false, length = 64)
  private String clOrdId;

  @Column(name = "symbol", nullable = false, length = 32)
  private String symbol;

  @Column(name = "side", nullable = false, length = 8)
  private String side;

  @Column(name = "exec_qty", nullable = false, precision = 19, scale = 4)
  private BigDecimal execQty;

  @Column(name = "exec_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal execPrice;

  @Column(name = "executed_at", nullable = false)
  private Instant executedAt;

  protected Execution() {
  }

  private Execution(
      Long orderId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal execQty,
      BigDecimal execPrice,
      Instant executedAt
  ) {
    this.orderId = orderId;
    this.accountId = accountId;
    this.clOrdId = clOrdId;
    this.symbol = symbol;
    this.side = side;
    this.execQty = execQty;
    this.execPrice = execPrice;
    this.executedAt = executedAt;
  }

  public static Execution of(
      Long orderId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal execQty,
      BigDecimal execPrice
  ) {
    return new Execution(orderId, accountId, clOrdId, symbol, side, execQty, execPrice, Instant.now());
  }

  public static Execution of(
      Long orderId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal execQty,
      BigDecimal execPrice,
      Instant executedAt
  ) {
    return new Execution(orderId, accountId, clOrdId, symbol, side, execQty, execPrice, executedAt);
  }

  public Long getId() {
    return id;
  }

  public Long getOrderId() {
    return orderId;
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

  public BigDecimal getExecQty() {
    return execQty;
  }

  public BigDecimal getExecPrice() {
    return execPrice;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }
}
