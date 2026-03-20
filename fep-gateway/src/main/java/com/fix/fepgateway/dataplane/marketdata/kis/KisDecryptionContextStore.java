package com.fix.fepgateway.dataplane.marketdata.kis;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class KisDecryptionContextStore {

  private final Map<String, KisDecryptionContext> decryptionContextsByTrId = new ConcurrentHashMap<>();

  public void put(String trId, KisDecryptionContext decryptionContext) {
    if (trId == null || trId.isBlank()) {
      throw new IllegalArgumentException("trId must not be blank");
    }
    if (decryptionContext == null) {
      throw new IllegalArgumentException("decryptionContext must not be null");
    }
    decryptionContextsByTrId.put(trId, decryptionContext);
  }

  public Optional<KisDecryptionContext> find(String trId) {
    if (trId == null || trId.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(decryptionContextsByTrId.get(trId));
  }

  public void clear() {
    decryptionContextsByTrId.clear();
  }
}
