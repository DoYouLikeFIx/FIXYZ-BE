package com.fix.fepgateway.vo;

import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
import java.time.Instant;

public record GatewayOrderSubmitCommand(
    String clOrdId,
    String accountId,
    String symbol,
    FepSecurityExchange securityExchange,
    FepSide side,
    FepOrderType orderType,
    Long qty,
    Long price,
    String quoteSnapshotId,
    Instant quoteAsOf,
    FepQuoteSourceMode quoteSourceMode,
    Long preTradePrice,
    String currency,
    String referenceId
) {
}
