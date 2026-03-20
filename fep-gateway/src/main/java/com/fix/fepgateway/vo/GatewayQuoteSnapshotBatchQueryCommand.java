package com.fix.fepgateway.vo;

import com.fix.common.fep.FepQuoteSourceMode;
import java.util.List;

public record GatewayQuoteSnapshotBatchQueryCommand(
    List<String> symbols,
    FepQuoteSourceMode quoteSourceMode
) {
}
