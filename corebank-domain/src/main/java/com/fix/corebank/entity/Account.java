package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_no", nullable = false, unique = true, length = 14)
  private String accountNo;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  @Column(name = "currency", nullable = false, length = 16)
  private String currency;

  @Column(name = "cash_balance", nullable = false, precision = 19, scale = 4)
  private BigDecimal cashBalance;

  @Column(name = "daily_sell_limit", nullable = false, precision = 19, scale = 4)
  private BigDecimal dailySellLimit;

  protected Account() {
  }

  private Account(
      String accountNo,
      Long memberId,
      String status,
      String currency,
      BigDecimal cashBalance,
      BigDecimal dailySellLimit
  ) {
    this.accountNo = accountNo;
    this.memberId = memberId;
    this.status = status;
    this.currency = currency;
    this.cashBalance = cashBalance;
    this.dailySellLimit = dailySellLimit;
  }

  public static Account of(
      String accountNo,
      Long memberId,
      String currency,
      BigDecimal cashBalance,
      BigDecimal dailySellLimit
  ) {
    return new Account(accountNo, memberId, "ACTIVE", currency, cashBalance, dailySellLimit);
  }

  public static Account of(
      String accountNo,
      Long memberId,
      String status,
      String currency,
      BigDecimal cashBalance,
      BigDecimal dailySellLimit
  ) {
    return new Account(accountNo, memberId, status, currency, cashBalance, dailySellLimit);
  }

  public Long getId() {
    return id;
  }

  public String getAccountNo() {
    return accountNo;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getStatus() {
    return status;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getCashBalance() {
    return cashBalance;
  }

  public BigDecimal getDailySellLimit() {
    return dailySellLimit;
  }

  public void updateStatus(String status) {
    this.status = status;
  }
}
