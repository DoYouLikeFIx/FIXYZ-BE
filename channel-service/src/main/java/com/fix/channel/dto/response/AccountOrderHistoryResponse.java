package com.fix.channel.dto.response;

import com.fix.channel.vo.AccountOrderHistoryItemResult;
import com.fix.channel.vo.AccountOrderHistoryResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class AccountOrderHistoryResponse {

  private final List<Item> content;
  private final long totalElements;
  private final int totalPages;
  private final int number;
  private final int size;

  private AccountOrderHistoryResponse(
      List<Item> content,
      long totalElements,
      int totalPages,
      int number,
      int size
  ) {
    this.content = content;
    this.totalElements = totalElements;
    this.totalPages = totalPages;
    this.number = number;
    this.size = size;
  }

  public static AccountOrderHistoryResponse from(AccountOrderHistoryResult result) {
    List<Item> content = result.getContent().stream()
        .map(Item::from)
        .toList();
    return new AccountOrderHistoryResponse(
        content,
        result.getTotalElements(),
        result.getTotalPages(),
        result.getNumber(),
        result.getSize()
    );
  }

  public List<Item> getContent() {
    return content;
  }

  public long getTotalElements() {
    return totalElements;
  }

  public int getTotalPages() {
    return totalPages;
  }

  public int getNumber() {
    return number;
  }

  public int getSize() {
    return size;
  }

  public static class Item {

    private final String symbol;
    private final String symbolName;
    private final String side;
    private final BigDecimal qty;
    private final BigDecimal unitPrice;
    private final BigDecimal totalAmount;
    private final String status;
    private final String clOrdId;
    private final Instant createdAt;

    private Item(
        String symbol,
        String symbolName,
        String side,
        BigDecimal qty,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String status,
        String clOrdId,
        Instant createdAt
    ) {
      this.symbol = symbol;
      this.symbolName = symbolName;
      this.side = side;
      this.qty = qty;
      this.unitPrice = unitPrice;
      this.totalAmount = totalAmount;
      this.status = status;
      this.clOrdId = clOrdId;
      this.createdAt = createdAt;
    }

    private static Item from(AccountOrderHistoryItemResult result) {
      return new Item(
          result.getSymbol(),
          result.getSymbolName(),
          result.getSide(),
          result.getQty(),
          result.getUnitPrice(),
          result.getTotalAmount(),
          result.getStatus(),
          result.getClOrdId(),
          result.getCreatedAt()
      );
    }

    public String getSymbol() {
      return symbol;
    }

    public String getSymbolName() {
      return symbolName;
    }

    public String getSide() {
      return side;
    }

    public BigDecimal getQty() {
      return qty;
    }

    public BigDecimal getUnitPrice() {
      return unitPrice;
    }

    public BigDecimal getTotalAmount() {
      return totalAmount;
    }

    public String getStatus() {
      return status;
    }

    public String getClOrdId() {
      return clOrdId;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }
  }
}
