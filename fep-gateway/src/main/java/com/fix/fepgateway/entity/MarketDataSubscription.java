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
@Table(name = "fep_market_data_subscriptions")
public class MarketDataSubscription extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subscription_id", nullable = false, unique = true, length = 36)
  private String subscriptionId;

  @Column(name = "provider", nullable = false, length = 32)
  private String provider;

  @Column(name = "symbol", nullable = false, length = 16)
  private String symbol;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_mode", nullable = false, length = 16)
  private FepQuoteSourceMode sourceMode;

  @Column(name = "tr_id", length = 32)
  private String trId;

  @Column(name = "tr_key", length = 32)
  private String trKey;

  @Column(name = "last_event_offset")
  private Long lastEventOffset;

  @Column(name = "last_quote_as_of")
  private Instant lastQuoteAsOf;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  protected MarketDataSubscription() {
  }

  private MarketDataSubscription(
      String subscriptionId,
      String provider,
      String symbol,
      FepQuoteSourceMode sourceMode,
      String trId,
      String trKey,
      Long lastEventOffset,
      Instant lastQuoteAsOf,
      boolean active
  ) {
    this.subscriptionId = subscriptionId;
    this.provider = provider;
    this.symbol = symbol;
    this.sourceMode = sourceMode;
    this.trId = trId;
    this.trKey = trKey;
    this.lastEventOffset = lastEventOffset;
    this.lastQuoteAsOf = lastQuoteAsOf;
    this.active = active;
  }

  public static MarketDataSubscription active(
      String subscriptionId,
      String provider,
      String symbol,
      FepQuoteSourceMode sourceMode,
      String trId,
      String trKey
  ) {
    return new MarketDataSubscription(
        subscriptionId,
        provider,
        symbol,
        sourceMode,
        trId,
        trKey,
        null,
        null,
        true
    );
  }

  public Long getId() {
    return id;
  }

  public String getSubscriptionId() {
    return subscriptionId;
  }

  public String getProvider() {
    return provider;
  }

  public String getSymbol() {
    return symbol;
  }

  public FepQuoteSourceMode getSourceMode() {
    return sourceMode;
  }

  public String getTrId() {
    return trId;
  }

  public String getTrKey() {
    return trKey;
  }

  public Long getLastEventOffset() {
    return lastEventOffset;
  }

  public Instant getLastQuoteAsOf() {
    return lastQuoteAsOf;
  }

  public boolean isActive() {
    return active;
  }

  public void updateProgress(Long lastEventOffset, Instant lastQuoteAsOf) {
    this.lastEventOffset = lastEventOffset;
    this.lastQuoteAsOf = lastQuoteAsOf;
  }

  public void activate() {
    this.active = true;
  }

  public void deactivate() {
    this.active = false;
  }
}
