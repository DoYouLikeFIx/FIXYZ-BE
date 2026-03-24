package com.fix.corebank.entity;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

  @Column(name = "execution_seq", nullable = false)
  private Integer executionSeq;

  @Column(name = "quote_snapshot_id", length = 64)
  private String quoteSnapshotId;

  @Column(name = "quote_as_of")
  private Instant quoteAsOf;

  @Enumerated(EnumType.STRING)
  @Column(name = "quote_source_mode", length = 16)
  private FepQuoteSourceMode quoteSourceMode;

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
      Integer executionSeq,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode,
      Instant executedAt
  ) {
    this.orderId = orderId;
    this.accountId = accountId;
    this.clOrdId = clOrdId;
    this.symbol = symbol;
    this.side = side;
    this.execQty = execQty;
    this.execPrice = execPrice;
    this.executionSeq = executionSeq;
    this.quoteSnapshotId = quoteSnapshotId;
    this.quoteAsOf = quoteAsOf;
    this.quoteSourceMode = quoteSourceMode;
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
    return new Execution(orderId, accountId, clOrdId, symbol, side, execQty, execPrice, 1, null, null, null, Instant.now());
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
    return new Execution(orderId, accountId, clOrdId, symbol, side, execQty, execPrice, 1, null, null, null, executedAt);
  }

  public static Execution of(
      Long orderId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal execQty,
      BigDecimal execPrice,
      Integer executionSeq,
      Instant executedAt
  ) {
    return new Execution(orderId, accountId, clOrdId, symbol, side, execQty, execPrice, executionSeq, null, null, null, executedAt);
  }

  public static Execution of(
      Long orderId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal execQty,
      BigDecimal execPrice,
      Integer executionSeq,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode,
      Instant executedAt
  ) {
    return new Execution(
        orderId,
        accountId,
        clOrdId,
        symbol,
        side,
        execQty,
        execPrice,
        executionSeq,
        quoteSnapshotId,
        quoteAsOf,
        quoteSourceMode,
        executedAt
    );
  }

  public static Execution of(
      Long orderId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal execQty,
      BigDecimal execPrice,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode,
      Instant executedAt
  ) {
    return of(
        orderId,
        accountId,
        clOrdId,
        symbol,
        side,
        execQty,
        execPrice,
        1,
        quoteSnapshotId,
        quoteAsOf,
        quoteSourceMode,
        executedAt
    );
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

  public Integer getExecutionSeq() {
    return executionSeq;
  }

  public String getQuoteSnapshotId() {
    return quoteSnapshotId;
  }

  public Instant getQuoteAsOf() {
    return quoteAsOf;
  }

  public FepQuoteSourceMode getQuoteSourceMode() {
    return quoteSourceMode;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }
}
