package com.fix.fepgateway.dataplane.marketdata.kis;

import java.util.List;
import java.util.Objects;

public record KisH0stcnt0Record(List<String> fields) {

  public static final String TR_ID = "H0STCNT0";
  public static final int RECORD_FIELD_COUNT = 46;

  private static final int SYMBOL_INDEX = 0;
  private static final int TRADE_HOUR_INDEX = 1;
  private static final int LAST_TRADE_INDEX = 2;
  private static final int BEST_ASK_INDEX = 10;
  private static final int BEST_BID_INDEX = 11;
  private static final int BUSINESS_DATE_INDEX = 33;
  private static final int MARKET_PHASE_CODE_INDEX = 34;
  private static final int TRADING_HALT_INDEX = 35;
  private static final int VI_REFERENCE_PRICE_INDEX = 45;

  public KisH0stcnt0Record {
    Objects.requireNonNull(fields, "fields must not be null");
    if (fields.size() != RECORD_FIELD_COUNT) {
      throw new IllegalArgumentException("H0STCNT0 record must contain exactly 46 fields");
    }
    fields = List.copyOf(fields);
  }

  public String symbol() {
    return field(SYMBOL_INDEX);
  }

  public String tradeHour() {
    return field(TRADE_HOUR_INDEX);
  }

  public String lastTrade() {
    return field(LAST_TRADE_INDEX);
  }

  public String bestAsk() {
    return field(BEST_ASK_INDEX);
  }

  public String bestBid() {
    return field(BEST_BID_INDEX);
  }

  public String businessDate() {
    return field(BUSINESS_DATE_INDEX);
  }

  public String marketPhaseCode() {
    return field(MARKET_PHASE_CODE_INDEX);
  }

  public String tradingHalt() {
    return field(TRADING_HALT_INDEX);
  }

  public String viReferencePrice() {
    return field(VI_REFERENCE_PRICE_INDEX);
  }

  public String field(int index) {
    return fields.get(index);
  }
}
