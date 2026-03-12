package com.fix.channel.vo;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class OrderSessionCreateCommand {

  private final Long memberId;
  private final Long accountId;
  private final String clOrdId;
  private final String symbol;
  private final String side;
  private final String orderType;
  private final BigDecimal qty;
  private final BigDecimal price;

  private OrderSessionCreateCommand(
      Long memberId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price
  ) {
    this.memberId = memberId;
    this.accountId = accountId;
    this.clOrdId = clOrdId;
    this.symbol = symbol;
    this.side = side;
    this.orderType = orderType;
    this.qty = qty;
    this.price = price;
  }

  public static OrderSessionCreateCommand of(
      Long memberId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price
  ) {
    return new OrderSessionCreateCommand(memberId, accountId, clOrdId, symbol, side, orderType, qty, price);
  }

  public Long getMemberId() {
    return memberId;
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

  public String getOrderType() {
    return orderType;
  }

  public BigDecimal getQty() {
    return qty;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String replayFingerprint() {
    return sha256Hex(String.join(
        "|",
        String.valueOf(accountId),
        symbol,
        side,
        orderType,
        normalizeDecimal(qty),
        normalizeDecimal(price)
    ));
  }

  private String normalizeDecimal(BigDecimal value) {
    if (value == null) {
      return "null";
    }
    return value.stripTrailingZeros().toPlainString();
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hash.length * 2);
      for (byte current : hash) {
        builder.append(String.format("%02x", current));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }
}
