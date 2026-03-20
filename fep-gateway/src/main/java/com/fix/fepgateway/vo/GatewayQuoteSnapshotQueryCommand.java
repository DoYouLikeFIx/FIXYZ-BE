package com.fix.fepgateway.vo;

import com.fix.common.fep.FepQuoteSourceMode;

public record GatewayQuoteSnapshotQueryCommand(
    String symbol,
    FepQuoteSourceMode quoteSourceMode
) {
}
