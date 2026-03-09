package com.fix.fepgateway.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fix.fepgateway.vo.GatewayInternalOrderStatusCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record FepInternalOrderStatusRequest(
    @NotBlank String status,
    @Schema(description = "Optional executed quantity for status correction paths such as PARTIALLY_FILLED.")
    @Positive Long executedQty,
    @Schema(description = "Optional executed price for MARKET FILLED or partial execution correction paths.")
    @Positive Long executedPrice,
    @Schema(description = "Optional recovery state override. Use ESCALATED before manual replay.")
    String recoveryStatus,
    @Schema(description = "Optional simulated requery outcome for replay paths.", allowableValues = {
        "FILLED", "PARTIALLY_FILLED", "CANCELED", "UNKNOWN", "PENDING", "MALFORMED", "REJECTED"
    })
    String requeryStatus,
    @Schema(description = "Optional executed quantity to pair with a simulated requery outcome.")
    @PositiveOrZero Long requeryExecutedQty,
    @Schema(description = "Optional executed price to pair with a simulated requery outcome.")
    @Positive Long requeryExecutedPrice,
    @Schema(description = "Optional cancel transport simulation mode.", allowableValues = {"NONE", "TIMEOUT", "REJECT"})
    String cancelFailureMode,
    @Schema(description = "Optional reference price override for legacy MARKET recovery rows.")
    @Positive Long referencePrice
) {

  public GatewayInternalOrderStatusCommand toVo(String clOrdId) {
    return GatewayInternalOrderStatusCommand.of(
        clOrdId,
        status,
        executedQty,
        executedPrice,
        recoveryStatus,
        requeryStatus,
        requeryExecutedQty,
        requeryExecutedPrice,
        cancelFailureMode,
        referencePrice
    );
  }
}
