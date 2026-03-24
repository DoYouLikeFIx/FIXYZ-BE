package com.fix.channel.dto.response;

import com.fix.channel.vo.AccountOrderHistoryItemResult;
import com.fix.channel.vo.AccountOrderHistoryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AccountOrderHistoryResponse(
    List<Item> content,
    long totalElements,
    int totalPages,
    int number,
    int size
) {

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

  @Schema(name = "AccountOrderHistoryItem")
  public record Item(
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
  }
}
