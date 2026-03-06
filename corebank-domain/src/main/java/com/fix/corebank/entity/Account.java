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

  @Column(name = "account_no", nullable = false, unique = true, length = 64)
  private String accountNo;

  @Column(name = "member_no", nullable = false, length = 64)
  private String memberNo;

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
      String memberNo,
      String currency,
      BigDecimal cashBalance,
      BigDecimal dailySellLimit
  ) {
    this.accountNo = accountNo;
    this.memberNo = memberNo;
    this.currency = currency;
    this.cashBalance = cashBalance;
    this.dailySellLimit = dailySellLimit;
  }

  public static Account of(
      String accountNo,
      String memberNo,
      String currency,
      BigDecimal cashBalance,
      BigDecimal dailySellLimit
  ) {
    return new Account(accountNo, memberNo, currency, cashBalance, dailySellLimit);
  }

  public Long getId() {
    return id;
  }

  public String getAccountNo() {
    return accountNo;
  }

  public String getMemberNo() {
    return memberNo;
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
}
