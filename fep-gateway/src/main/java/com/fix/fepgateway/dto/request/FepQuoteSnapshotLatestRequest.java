package com.fix.fepgateway.dto.request;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.vo.GatewayQuoteSnapshotQueryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FepQuoteSnapshotLatestRequest(
    @NotBlank
    @Schema(description = "KRX symbol code used to query the latest stored quote snapshot.")
    String symbol,
    @NotNull
    @Schema(description = "Quote source mode to query.", allowableValues = {"LIVE", "DELAYED", "REPLAY"})
    FepQuoteSourceMode quoteSourceMode
) {

  public GatewayQuoteSnapshotQueryCommand toVo() {
    return new GatewayQuoteSnapshotQueryCommand(symbol, quoteSourceMode);
  }
}
