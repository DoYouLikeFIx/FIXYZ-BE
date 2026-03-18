package com.fix.channel.dto.response;

import com.fix.channel.vo.AdminAuditLogItemVo;
import com.fix.channel.vo.AdminAuditLogQueryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record AdminAuditLogQueryResponse(
    List<Item> content,
    long totalElements,
    int totalPages,
    int number,
    int size
) {

  public static AdminAuditLogQueryResponse from(AdminAuditLogQueryResult result) {
    return new AdminAuditLogQueryResponse(
        result.getContent().stream().map(Item::from).toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        result.getNumber(),
        result.getSize()
    );
  }

  @Schema(name = "AdminAuditLogItem")
  public record Item(
      String auditId,
      Long memberId,
      String memberUuid,
      String email,
      String eventType,
      String ipAddress,
      String userAgent,
      String description,
      String clOrdId,
      Long orderSessionId,
      Instant createdAt
  ) {

    private static Item from(AdminAuditLogItemVo item) {
      return new Item(
          item.getAuditId(),
          item.getMemberId(),
          item.getMemberUuid(),
          item.getEmail(),
          item.getEventType(),
          item.getIpAddress(),
          item.getUserAgent(),
          item.getDescription(),
          item.getClOrdId(),
          item.getOrderSessionId(),
          item.getCreatedAt()
      );
    }
  }
}
