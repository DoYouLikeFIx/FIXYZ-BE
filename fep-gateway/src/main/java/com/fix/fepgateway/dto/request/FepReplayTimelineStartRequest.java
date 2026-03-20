package com.fix.fepgateway.dto.request;

import com.fix.common.validation.ContractPatterns;
import com.fix.fepgateway.vo.GatewayReplayTimelineStartCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record FepReplayTimelineStartRequest(
    @NotBlank
    @Pattern(regexp = ContractPatterns.SIX_DIGIT_SYMBOL)
    @Schema(description = "KRX symbol code used to start the replay timeline.")
    String symbol,
    @NotBlank
    @Schema(description = "Deterministic replay seed used to derive timeline events.")
    String seed,
    @Min(0)
    @Schema(description = "Starting replay cursor offset.", minimum = "0")
    long startOffset,
    @Positive
    @Schema(description = "Replay speed factor applied on each drain cycle.")
    BigDecimal speedFactor
) {

  public GatewayReplayTimelineStartCommand toVo() {
    return new GatewayReplayTimelineStartCommand(symbol, seed, startOffset, speedFactor);
  }
}
