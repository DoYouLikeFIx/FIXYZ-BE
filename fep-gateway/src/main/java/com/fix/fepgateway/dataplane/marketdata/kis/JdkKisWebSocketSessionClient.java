package com.fix.fepgateway.dataplane.marketdata.kis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class JdkKisWebSocketSessionClient implements KisWebSocketSessionClient {

  private final HttpClient httpClient;

  public JdkKisWebSocketSessionClient() {
    this(HttpClient.newHttpClient());
  }

  JdkKisWebSocketSessionClient(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public KisWebSocketSession connect(URI uri, Consumer<String> inboundTextHandler) {
    if (uri == null) {
      throw new IllegalArgumentException("uri must not be null");
    }
    if (inboundTextHandler == null) {
      throw new IllegalArgumentException("inboundTextHandler must not be null");
    }

    AggregatingListener listener = new AggregatingListener(inboundTextHandler);
    try {
      WebSocket webSocket = httpClient.newWebSocketBuilder()
          .buildAsync(uri, listener)
          .join();
      listener.markOpen();
      return new JdkKisWebSocketSession(webSocket, listener.openState());
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Failed to connect to KIS websocket endpoint: " + uri, exception);
    }
  }

  private static final class JdkKisWebSocketSession implements KisWebSocketSession {

    private final WebSocket webSocket;
    private final AtomicBoolean open;

    private JdkKisWebSocketSession(WebSocket webSocket, AtomicBoolean open) {
      this.webSocket = webSocket;
      this.open = open;
    }

    @Override
    public void sendText(String payload) {
      if (payload == null || payload.isBlank()) {
        throw new IllegalArgumentException("payload must not be blank");
      }
      webSocket.sendText(payload, true).join();
    }

    @Override
    public void close() {
      if (open.compareAndSet(true, false)) {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "normal closure").join();
      }
    }

    @Override
    public boolean isOpen() {
      return open.get();
    }
  }

  private static final class AggregatingListener implements WebSocket.Listener {

    private final Consumer<String> inboundTextHandler;
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final StringBuilder textBuffer = new StringBuilder();

    private AggregatingListener(Consumer<String> inboundTextHandler) {
      this.inboundTextHandler = inboundTextHandler;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
      WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      textBuffer.append(data);
      if (last) {
        inboundTextHandler.accept(textBuffer.toString());
        textBuffer.setLength(0);
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      open.set(false);
      return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      open.set(false);
      WebSocket.Listener.super.onError(webSocket, error);
    }

    private void markOpen() {
      open.set(true);
    }

    private AtomicBoolean openState() {
      return open;
    }
  }
}
