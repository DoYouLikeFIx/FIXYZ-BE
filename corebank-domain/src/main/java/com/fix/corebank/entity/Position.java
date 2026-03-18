package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(
    name = "positions",
    uniqueConstraints = @UniqueConstraint(name = "uk_positions_account_symbol", columnNames = {"account_id", "symbol"})
)
public class Position extends BaseTimeEntity {

  private static final int SCALE = 4;

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

  public void applyBuy(BigDecimal executedQty, BigDecimal executedPrice) {
    BigDecimal normalizedQty = normalizePositive(executedQty, "executed quantity is required");
    BigDecimal normalizedPrice = normalizePositive(executedPrice, "executed price is required");

    BigDecimal existingCostBasis = qty.multiply(avgPrice);
    BigDecimal additionalCostBasis = normalizedQty.multiply(normalizedPrice);
    BigDecimal nextQty = qty.add(normalizedQty).setScale(SCALE, RoundingMode.HALF_UP);
    BigDecimal nextAvgPrice = existingCostBasis.add(additionalCostBasis)
        .divide(nextQty, SCALE, RoundingMode.HALF_UP);

    this.qty = nextQty;
    this.avgPrice = nextAvgPrice;
  }

  public void applySell(BigDecimal executedQty) {
    BigDecimal normalizedQty = normalizePositive(executedQty, "executed quantity is required");
    if (qty.compareTo(normalizedQty) < 0) {
      throw new BusinessException(ErrorCode.ORD_INSUFFICIENT_POSITION, "insufficient position quantity");
    }

    BigDecimal nextQty = qty.subtract(normalizedQty).setScale(SCALE, RoundingMode.HALF_UP);
    this.qty = nextQty;
    if (nextQty.signum() == 0) {
      this.avgPrice = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }
  }

  public void rebuildTo(BigDecimal rebuiltQty, BigDecimal rebuiltAvgPrice) {
    if (rebuiltQty == null || rebuiltAvgPrice == null) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "rebuilt position values are required");
    }
    BigDecimal normalizedQty = rebuiltQty.setScale(SCALE, RoundingMode.HALF_UP);
    BigDecimal normalizedAvgPrice = rebuiltAvgPrice.setScale(SCALE, RoundingMode.HALF_UP);
    if (normalizedQty.signum() < 0) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "rebuilt quantity must be non-negative");
    }
    if (normalizedAvgPrice.signum() < 0) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "rebuilt average price must be non-negative");
    }
    if (normalizedQty.signum() == 0) {
      normalizedAvgPrice = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }
    this.qty = normalizedQty;
    this.avgPrice = normalizedAvgPrice;
  }

  private BigDecimal normalizePositive(BigDecimal value, String nullMessage) {
    if (value == null) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, nullMessage);
    }
    BigDecimal normalized = value.setScale(SCALE, RoundingMode.HALF_UP);
    if (normalized.signum() <= 0) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "position mutation inputs must be positive");
    }
    return normalized;
  }
}
