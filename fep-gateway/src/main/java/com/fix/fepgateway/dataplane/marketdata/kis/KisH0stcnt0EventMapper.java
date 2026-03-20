package com.fix.fepgateway.dataplane.marketdata.kis;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class KisH0stcnt0EventMapper {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter BUSINESS_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter TRADE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

  public NormalizedQuoteEvent toLiveEvent(KisH0stcnt0Record record, long streamOffset) {
    Long bestBid = parseNullableLong(record.bestBid());
    Long bestAsk = parseNullableLong(record.bestAsk());

    if (bestBid != null && bestAsk != null && bestBid > bestAsk) {
      throw new IllegalArgumentException("KIS H0STCNT0 bestBid must not exceed bestAsk");
    }

    return new NormalizedQuoteEvent(
        "KIS",
        record.symbol(),
        FepQuoteSourceMode.LIVE,
        toQuoteAsOf(record.businessDate(), record.tradeHour()),
        bestBid,
        bestAsk,
        parseNullableLong(record.lastTrade()),
        streamOffset,
        false
    );
  }

  private Instant toQuoteAsOf(String businessDate, String tradeHour) {
    if (businessDate == null || businessDate.isBlank()) {
      throw new IllegalArgumentException("KIS H0STCNT0 businessDate must not be blank");
    }
    if (tradeHour == null || tradeHour.isBlank()) {
      throw new IllegalArgumentException("KIS H0STCNT0 tradeHour must not be blank");
    }

    LocalDate date = LocalDate.parse(businessDate, BUSINESS_DATE_FORMATTER);
    String normalizedTradeHour = String.format("%06d", Integer.parseInt(tradeHour));
    LocalTime time = LocalTime.parse(normalizedTradeHour, TRADE_TIME_FORMATTER);
    return LocalDateTime.of(date, time).atZone(KST).toInstant();
  }

  private Long parseNullableLong(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Long.parseLong(value);
  }
}
