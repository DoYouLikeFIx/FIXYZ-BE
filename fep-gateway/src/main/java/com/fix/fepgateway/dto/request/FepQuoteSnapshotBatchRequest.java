package com.fix.fepgateway.dto.request;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.vo.GatewayQuoteSnapshotBatchQueryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record FepQuoteSnapshotBatchRequest(
    @NotEmpty
    @Schema(description = "KRX symbol codes used to query the latest stored quote snapshots.")
    List<@NotBlank String> symbol,
    @NotNull
    @Schema(description = "Quote source mode to query.", allowableValues = {"LIVE", "DELAYED", "REPLAY"})
    FepQuoteSourceMode quoteSourceMode
) {

  public GatewayQuoteSnapshotBatchQueryCommand toVo() {
    return new GatewayQuoteSnapshotBatchQueryCommand(symbol, quoteSourceMode);
  }
}
