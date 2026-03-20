package com.fix.fepgateway.dataplane.marketdata.kis;

import java.net.URI;
import java.util.function.Consumer;

public interface KisWebSocketSessionClient {

  KisWebSocketSession connect(URI uri, Consumer<String> inboundTextHandler);
}
