package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KisDecryptionContextStoreTest {

  private final KisDecryptionContextStore store = new KisDecryptionContextStore();

  @Test
  void shouldStoreAndRetrieveDecryptionContextByTrId() {
    KisDecryptionContext decryptionContext = new KisDecryptionContext(
        "12345678901234567890123456789012",
        "1234567890123456"
    );

    store.put("H0STCNT0", decryptionContext);

    assertThat(store.find("H0STCNT0")).contains(decryptionContext);
  }

  @Test
  void shouldClearAllCachedContexts() {
    store.put(
        "H0STCNT0",
        new KisDecryptionContext("12345678901234567890123456789012", "1234567890123456")
    );

    store.clear();

    assertThat(store.find("H0STCNT0")).isEmpty();
  }
}
