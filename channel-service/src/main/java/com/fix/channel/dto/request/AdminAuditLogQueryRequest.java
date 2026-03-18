package com.fix.channel.dto.request;

import com.fix.channel.vo.AdminAuditLogQueryCommand;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;

public record AdminAuditLogQueryRequest(
    @Min(0)
    Integer page,

    @Min(1)
    @Max(100)
    Integer size,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant from,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant to,

    Long memberId,

    String eventType
) {

  public AdminAuditLogQueryRequest {
    page = page == null ? 0 : page;
    size = size == null ? 20 : size;
    if (from != null && to != null && from.isAfter(to)) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "from must be before or equal to to");
    }
  }

  public AdminAuditLogQueryCommand toVo() {
    return AdminAuditLogQueryCommand.of(page, size, from, to, memberId, eventType);
  }
}
