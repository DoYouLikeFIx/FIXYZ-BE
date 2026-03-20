package com.fix.fepgateway.entity;

import com.fix.common.entity.BaseTimeEntity;
import com.fix.common.fep.FepQuoteSourceMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "fep_quote_snapshots")
public class QuoteSnapshot extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "quote_snapshot_id", nullable = false, unique = true, length = 128)
  private String quoteSnapshotId;

  @Column(name = "symbol", nullable = false, length = 16)
  private String symbol;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_mode", nullable = false, length = 16)
  private FepQuoteSourceMode sourceMode;

  @Column(name = "quote_as_of", nullable = false)
  private Instant quoteAsOf;

  @Column(name = "best_bid")
  private Long bestBid;

  @Column(name = "best_ask")
  private Long bestAsk;

  @Column(name = "last_trade")
  private Long lastTrade;

  @Column(name = "stream_offset", nullable = false)
  private Long streamOffset;

  @Column(name = "is_stale", nullable = false)
  private boolean stale;

  protected QuoteSnapshot() {
  }

  private QuoteSnapshot(
      String quoteSnapshotId,
      String symbol,
      FepQuoteSourceMode sourceMode,
      Instant quoteAsOf,
      Long bestBid,
      Long bestAsk,
      Long lastTrade,
      Long streamOffset,
      boolean stale
  ) {
    this.quoteSnapshotId = quoteSnapshotId;
    this.symbol = symbol;
    this.sourceMode = sourceMode;
    this.quoteAsOf = quoteAsOf;
    this.bestBid = bestBid;
    this.bestAsk = bestAsk;
    this.lastTrade = lastTrade;
    this.streamOffset = streamOffset;
    this.stale = stale;
  }

  public static QuoteSnapshot recorded(
      String quoteSnapshotId,
      String symbol,
      FepQuoteSourceMode sourceMode,
      Instant quoteAsOf,
      Long bestBid,
      Long bestAsk,
      Long lastTrade,
      Long streamOffset,
      boolean stale
  ) {
    return new QuoteSnapshot(
        quoteSnapshotId,
        symbol,
        sourceMode,
        quoteAsOf,
        bestBid,
        bestAsk,
        lastTrade,
        streamOffset,
        stale
    );
  }

  public Long getId() {
    return id;
  }

  public String getQuoteSnapshotId() {
    return quoteSnapshotId;
  }

  public String getSymbol() {
    return symbol;
  }

  public FepQuoteSourceMode getSourceMode() {
    return sourceMode;
  }

  public Instant getQuoteAsOf() {
    return quoteAsOf;
  }

  public Long getBestBid() {
    return bestBid;
  }

  public Long getBestAsk() {
    return bestAsk;
  }

  public Long getLastTrade() {
    return lastTrade;
  }

  public Long getStreamOffset() {
    return streamOffset;
  }

  public boolean isStale() {
    return stale;
  }
}
