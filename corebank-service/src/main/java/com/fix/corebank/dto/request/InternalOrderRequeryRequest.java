package com.fix.corebank.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import com.fix.corebank.vo.InternalOrderRequeryCommand;

public class InternalOrderRequeryRequest {

  @Schema(
      description = "1-based requery attempt count from the scheduler. Unresolved results escalate when this reaches recovery.max-retry-count.",
      minimum = "1",
      defaultValue = "1"
  )
  @Min(1)
  private Integer attemptCount = 1;

  public InternalOrderRequeryCommand toVo(String clOrdId) {
    return InternalOrderRequeryCommand.of(clOrdId, attemptCount == null ? 1 : attemptCount);
  }

  public Integer getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(Integer attemptCount) {
    this.attemptCount = attemptCount;
  }
}
