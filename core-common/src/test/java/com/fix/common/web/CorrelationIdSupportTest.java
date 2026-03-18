package com.fix.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CorrelationIdSupportTest {

  @Test
  void shouldNormalizeBlankCorrelationIdToNull() {
    assertNull(CorrelationIdSupport.normalize(null));
    assertNull(CorrelationIdSupport.normalize(" "));
  }

  @Test
  void shouldPreservePlatformCorrelationIdsUpToSixtyFourCharacters() {
    String correlationId = "trace-channel-auth-very-long-correlation-id-000001";

    assertEquals(correlationId, CorrelationIdSupport.normalize(correlationId));
  }

  @Test
  void shouldAllowExplicitCanonicalLengthNormalization() {
    String correlationId = "trace-channel-auth-very-long-correlation-id-000001";

    assertEquals(36, CorrelationIdSupport.normalize(correlationId, 36).length());
    assertEquals(correlationId.substring(0, 36), CorrelationIdSupport.normalize(correlationId, 36));
  }
}
