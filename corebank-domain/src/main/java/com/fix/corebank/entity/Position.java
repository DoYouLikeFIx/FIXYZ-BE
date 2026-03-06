package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

@Entity
@Table(
    name = "positions",
    uniqueConstraints = @UniqueConstraint(name = "uk_positions_account_symbol", columnNames = {"account_id", "symbol"})
)
public class Position extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "symbol", nullable = false, length = 32)
  private String symbol;

  @Column(name = "qty", nullable = false, precision = 19, scale = 4)
  private BigDecimal qty;

  @Column(name = "avg_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal avgPrice;

  protected Position() {
  }

  private Position(Long accountId, String symbol, BigDecimal qty, BigDecimal avgPrice) {
    this.accountId = accountId;
    this.symbol = symbol;
    this.qty = qty;
    this.avgPrice = avgPrice;
  }

  public static Position of(Long accountId, String symbol, BigDecimal qty, BigDecimal avgPrice) {
    return new Position(accountId, symbol, qty, avgPrice);
  }

  public Long getId() {
    return id;
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getSymbol() {
    return symbol;
  }

  public BigDecimal getQty() {
    return qty;
  }

  public BigDecimal getAvgPrice() {
    return avgPrice;
  }
}
