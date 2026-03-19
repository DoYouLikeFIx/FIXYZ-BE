package com.fix.fepgateway.dataplane.marketdata.kis;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.LiveMarketDataPersistencePort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataEventSink;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSourceAdapter;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

@Component
public class KisLiveMarketDataAdapter implements MarketDataSourceAdapter, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(KisLiveMarketDataAdapter.class);

  private final FepMarketDataProperties properties;
  private final KisApprovalKeyService approvalKeyService;
  private final KisWebSocketSessionClient sessionClient;
  private final KisWebSocketPayloadFactory payloadFactory;
  private final KisWebSocketControlMessageParser controlMessageParser;
  private final KisDecryptionContextStore decryptionContextStore;
  private final KisH0stcnt0RecordParser h0stcnt0RecordParser;
  private final KisH0stcnt0EventMapper h0stcnt0EventMapper;
  private final LiveMarketDataPersistencePort liveMarketDataPersistencePort;

  private final Map<String, ActiveSubscription> activeSubscriptions = new ConcurrentHashMap<>();
  private final Map<RemoteSubscriptionKey, Integer> remoteSubscriptionRefCounts = new ConcurrentHashMap<>();
  private final AtomicLong streamOffsetSequence = new AtomicLong(0);

  private volatile KisWebSocketSession session;

  public KisLiveMarketDataAdapter(
      FepMarketDataProperties properties,
      KisApprovalKeyService approvalKeyService,
      KisWebSocketSessionClient sessionClient,
      KisWebSocketPayloadFactory payloadFactory,
      KisWebSocketControlMessageParser controlMessageParser,
      KisDecryptionContextStore decryptionContextStore,
      KisH0stcnt0RecordParser h0stcnt0RecordParser,
      KisH0stcnt0EventMapper h0stcnt0EventMapper,
      LiveMarketDataPersistencePort liveMarketDataPersistencePort
  ) {
    this.properties = properties;
    this.approvalKeyService = approvalKeyService;
    this.sessionClient = sessionClient;
    this.payloadFactory = payloadFactory;
    this.controlMessageParser = controlMessageParser;
    this.decryptionContextStore = decryptionContextStore;
    this.h0stcnt0RecordParser = h0stcnt0RecordParser;
    this.h0stcnt0EventMapper = h0stcnt0EventMapper;
    this.liveMarketDataPersistencePort = liveMarketDataPersistencePort;
  }

  @Override
  public String provider() {
    return "KIS";
  }

  @Override
  public FepQuoteSourceMode sourceMode() {
    return FepQuoteSourceMode.LIVE;
  }

  @Override
  public synchronized void start(MarketDataSubscriptionSpec subscriptionSpec, MarketDataEventSink eventSink) {
    validateSubscriptionSpec(subscriptionSpec, eventSink);

    if (activeSubscriptions.containsKey(subscriptionSpec.subscriptionId())) {
      return;
    }

    ActiveSubscription activeSubscription = new ActiveSubscription(
        subscriptionSpec,
        eventSink,
        new RemoteSubscriptionKey(subscriptionSpec.trId(), subscriptionSpec.trKey())
    );
    boolean requiresSubscribe = !remoteSubscriptionRefCounts.containsKey(activeSubscription.remoteKey());

    try {
      ensureSession();
      activeSubscriptions.put(subscriptionSpec.subscriptionId(), activeSubscription);
      remoteSubscriptionRefCounts.merge(activeSubscription.remoteKey(), 1, Integer::sum);

      if (requiresSubscribe) {
        sendSubscribe(activeSubscription.spec());
        liveMarketDataPersistencePort.activateSubscription(activeSubscription.spec());
      }
    } catch (RuntimeException exception) {
      activeSubscriptions.remove(subscriptionSpec.subscriptionId());
      decrementRemoteSubscription(activeSubscription.remoteKey());
      closeSessionIfIdle();
      throw exception;
    }
  }

  @Override
  public synchronized void stop(String subscriptionId) {
    ActiveSubscription activeSubscription = activeSubscriptions.remove(subscriptionId);
    if (activeSubscription == null) {
      return;
    }

    boolean requiresUnsubscribe = decrementRemoteSubscription(activeSubscription.remoteKey()) == 0;
    if (requiresUnsubscribe && session != null && session.isOpen()) {
      sendUnsubscribe(activeSubscription.spec());
      liveMarketDataPersistencePort.deactivateSubscription(activeSubscription.spec());
    }

    closeSessionIfIdle();
  }

  public Optional<KisDecryptionContext> findDecryptionContext(String trId) {
    return decryptionContextStore.find(trId);
  }

  @Override
  public synchronized void destroy() {
    activeSubscriptions.clear();
    remoteSubscriptionRefCounts.clear();
    if (session != null) {
      session.close();
      session = null;
    }
    decryptionContextStore.clear();
  }

  void handleInboundText(String inboundText) {
    if (inboundText == null || inboundText.isBlank()) {
      return;
    }

    if (inboundText.stripLeading().startsWith("{")) {
      handleControlMessage(inboundText);
      return;
    }

    handleRealtimeFrame(inboundText);
  }

  private void ensureSession() {
    if (session != null && session.isOpen()) {
      return;
    }
    URI websocketUri = URI.create(KisApiEndpointResolver.resolveWebSocketBaseUrl(properties.getKis().getEnv()));
    session = sessionClient.connect(websocketUri, this::handleInboundText);
  }

  private void sendSubscribe(MarketDataSubscriptionSpec subscriptionSpec) {
    send(payloadFactory.createSubscribePayload(
        approvalKeyService.currentOrIssue().value(),
        properties.getKis().getWs().getCusttype(),
        subscriptionSpec
    ));
  }

  private void sendUnsubscribe(MarketDataSubscriptionSpec subscriptionSpec) {
    send(payloadFactory.createUnsubscribePayload(
        approvalKeyService.currentOrIssue().value(),
        properties.getKis().getWs().getCusttype(),
        subscriptionSpec
    ));
  }

  private void send(String payload) {
    if (session == null || !session.isOpen()) {
      throw new IllegalStateException("KIS websocket session is not open");
    }
    session.sendText(payload);
  }

  private void handleControlMessage(String inboundText) {
    try {
      controlMessageParser.parse(inboundText).ifPresent(controlMessage -> {
        if (controlMessage.isSubscribeSuccess() && controlMessage.hasDecryptionContext()) {
          decryptionContextStore.put(controlMessage.trId(), controlMessage.decryptionContext());
          log.debug("Cached KIS websocket decryption context for trId={}", controlMessage.trId());
        }
      });
    } catch (RuntimeException exception) {
      log.warn("Failed to handle KIS websocket control message", exception);
    }
  }

  private void handleRealtimeFrame(String inboundText) {
    try {
      var records = h0stcnt0RecordParser.parse(
          inboundText,
          decryptionContextStore.find(KisH0stcnt0Record.TR_ID).orElse(null)
      );

      for (KisH0stcnt0Record record : records) {
        MarketDataSubscriptionSpec persistenceSpec = resolvePersistenceSpec(record.symbol(), KisH0stcnt0Record.TR_ID);
        NormalizedQuoteEvent event = h0stcnt0EventMapper.toLiveEvent(
            record,
            streamOffsetSequence.getAndIncrement()
        );
        liveMarketDataPersistencePort.persistSnapshot(persistenceSpec, event);
        publishToMatchingSinks(record.symbol(), KisH0stcnt0Record.TR_ID, event);
      }
    } catch (RuntimeException exception) {
      log.warn("Failed to handle KIS realtime frame", exception);
    }
  }

  private MarketDataSubscriptionSpec resolvePersistenceSpec(String symbol, String trId) {
    return activeSubscriptions.values().stream()
        .map(ActiveSubscription::spec)
        .filter(spec -> trId.equals(spec.trId()) && symbol.equals(spec.trKey()))
        .findFirst()
        .orElseGet(() -> new MarketDataSubscriptionSpec(
            "kis-live-runtime-" + symbol,
            provider(),
            symbol,
            sourceMode(),
            trId,
            symbol
        ));
  }

  private void publishToMatchingSinks(String symbol, String trId, NormalizedQuoteEvent event) {
    activeSubscriptions.values().stream()
        .filter(activeSubscription -> trId.equals(activeSubscription.spec().trId()))
        .filter(activeSubscription -> symbol.equals(activeSubscription.spec().trKey()))
        .forEach(activeSubscription -> {
          try {
            activeSubscription.eventSink().accept(event);
          } catch (RuntimeException exception) {
            log.warn(
                "Failed to dispatch KIS market-data event to sink. subscriptionId={}",
                activeSubscription.spec().subscriptionId(),
                exception
            );
          }
        });
  }

  private int decrementRemoteSubscription(RemoteSubscriptionKey remoteKey) {
    Integer remaining = remoteSubscriptionRefCounts.computeIfPresent(
        remoteKey,
        (key, refCount) -> refCount > 1 ? refCount - 1 : null
    );
    return remaining == null ? 0 : remaining;
  }

  private void closeSessionIfIdle() {
    if (!activeSubscriptions.isEmpty()) {
      return;
    }
    if (session != null) {
      session.close();
      session = null;
    }
    decryptionContextStore.clear();
  }

  private void validateSubscriptionSpec(MarketDataSubscriptionSpec subscriptionSpec, MarketDataEventSink eventSink) {
    if (subscriptionSpec == null) {
      throw new IllegalArgumentException("subscriptionSpec must not be null");
    }
    if (eventSink == null) {
      throw new IllegalArgumentException("eventSink must not be null");
    }
    if (!provider().equalsIgnoreCase(subscriptionSpec.provider())) {
      throw new IllegalArgumentException("subscriptionSpec.provider must be KIS");
    }
    if (subscriptionSpec.sourceMode() != FepQuoteSourceMode.LIVE) {
      throw new IllegalArgumentException("subscriptionSpec.sourceMode must be LIVE");
    }
    if (subscriptionSpec.trId() == null || subscriptionSpec.trId().isBlank()) {
      throw new IllegalArgumentException("subscriptionSpec.trId must not be blank");
    }
    if (subscriptionSpec.trKey() == null || subscriptionSpec.trKey().isBlank()) {
      throw new IllegalArgumentException("subscriptionSpec.trKey must not be blank");
    }
  }

  private record ActiveSubscription(
      MarketDataSubscriptionSpec spec,
      MarketDataEventSink eventSink,
      RemoteSubscriptionKey remoteKey
  ) {
  }

  private record RemoteSubscriptionKey(String trId, String trKey) {
  }
}
