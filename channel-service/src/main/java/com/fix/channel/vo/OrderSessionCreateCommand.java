package com.fix.channel.vo;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

public record OrderSessionCreateCommand(
    Long memberId,
    Long accountId,
    String clOrdId,
    String symbol,
    String side,
    String orderType,
    BigDecimal qty,
    BigDecimal price,
    Instant lastMfaVerifiedAt,
    String loginClientIp,
    String loginUserAgent,
    String requestClientIp,
    String requestUserAgent
) {

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
    return of(memberId, accountId, clOrdId, symbol, side, orderType, qty, price, null, null, null, null, null);
  }

  public static OrderSessionCreateCommand of(
      Long memberId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      Instant lastMfaVerifiedAt
  ) {
    return of(memberId, accountId, clOrdId, symbol, side, orderType, qty, price, lastMfaVerifiedAt, null, null, null, null);
  }

  public static OrderSessionCreateCommand of(
      Long memberId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      Instant lastMfaVerifiedAt,
      String loginClientIp,
      String loginUserAgent,
      String requestClientIp,
      String requestUserAgent
  ) {
    return new OrderSessionCreateCommand(
        memberId,
        accountId,
        clOrdId,
        symbol,
        side,
        orderType,
        qty,
        price,
        lastMfaVerifiedAt,
        loginClientIp,
        loginUserAgent,
        requestClientIp,
        requestUserAgent
    );
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

  public Instant getLastMfaVerifiedAt() {
    return lastMfaVerifiedAt;
  }

  public String getLoginClientIp() {
    return loginClientIp;
  }

  public String getLoginUserAgent() {
    return loginUserAgent;
  }

  public String getRequestClientIp() {
    return requestClientIp;
  }

  public String getRequestUserAgent() {
    return requestUserAgent;
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
